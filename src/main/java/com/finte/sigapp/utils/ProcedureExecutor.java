package com.finte.sigapp.utils;

import jakarta.persistence.EntityManager;
import jakarta.persistence.ParameterMode;
import jakarta.persistence.StoredProcedureQuery;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Session;
import org.springframework.stereotype.Component;

import java.sql.CallableStatement;
import java.sql.Types;

/**
 * Utilitario genérico para ejecutar procedimientos y funciones almacenadas en
 * Oracle (PL/SQL).
 *
 * - Procedimientos (void) → usa JPA StoredProcedureQuery.
 * - Funciones con retorno <T> → usa JPA para tipos estándar (String, Integer,
 * Long...),
 * y Hibernate Session + CallableStatement para Boolean,
 * dado que JDBC/JPA no soportan nativamente el BOOLEAN de Oracle PL/SQL.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class
ProcedureExecutor {

    private final EntityManager em;

    /**
     * Ejecuta un procedimiento almacenado en Oracle que no retorna valor (void).
     *
     * @param catalogName   nombre del paquete PL/SQL (ej: "PKGCONTARBO")
     * @param procedureName nombre del procedimiento (ej: "proGeneContFisi")
     * @param params        parámetros de entrada: cada Object[] contiene {nombre,
     *                      valor, Class<?>}
     */
    public void ejecutarProcedimiento(String catalogName,
            String procedureName,
            Object[]... params) {

        String fullName = procedureName == "" ? procedureName : catalogName + "." + procedureName;

        log.info("Ejecutando procedimiento: {}", fullName);
        try {
            StoredProcedureQuery query = em.createStoredProcedureQuery(fullName);
            for (Object[] param : params) {
                String name = (String) param[0];
                Object value = param[1];
                Class<?> type = (Class<?>) param[2];
                query.registerStoredProcedureParameter(name, type, ParameterMode.IN);
                query.setParameter(name, value);
            }
            query.execute();
            log.info("Procedimiento {} ejecutado correctamente", fullName);
        } catch (Exception e) {
            log.error("Error ejecutando procedimiento {}: {}", fullName, e.getMessage(), e);
            throw new RuntimeException("Error ejecutando " + fullName + ": " + e.getMessage(), e);
        }
    }

    /**
     * Ejecuta una función PL/SQL de Oracle y retorna un valor del tipo genérico
     * {@code <T>}.
     *
     * <p>
     * Para tipos estándar (String, Integer, Long, Date, etc.) usa JPA
     * StoredProcedureQuery.
     * </p>
     * <p>
     * Para {@code Boolean}, usa Hibernate Session + CallableStatement porque JDBC
     * no soporta
     * nativamente el tipo BOOLEAN de Oracle PL/SQL. El BOOLEAN se mapea como
     * INTEGER (1=TRUE, 0=FALSE).
     * </p>
     *
     * <p>
     * <b>Ejemplo de uso:</b>
     * </p>
     * 
     * <pre>
     * // Para Boolean:
     * Boolean enConteo = executor.ejecutarFuncion("PKG", "fun_ValidaBodega", Boolean.class,
     *         new Object[] { "arg1", "arg2", fecha },
     *         new int[] { Types.VARCHAR, Types.VARCHAR, Types.DATE });
     *
     * // Para String:
     * String resultado = executor.ejecutarFuncion("PKG", "fun_ObtenerNombre", String.class,
     *         new Object[] { "codigo" },
     *         new int[] { Types.VARCHAR });
     * </pre>
     *
     * @param catalogName  nombre del paquete PL/SQL (ej: "PKGCONTARBO")
     * @param functionName nombre de la función (ej: "fun_ValidaBodega")
     * @param returnType   tipo de retorno esperado (ej: Boolean.class,
     *                     String.class, Integer.class)
     * @param bindValues   valores de los parámetros de entrada en orden posicional
     * @param sqlTypes     tipos SQL de cada parámetro (java.sql.Types)
     * @return el valor retornado por la función casteado al tipo {@code T}
     */
    @SuppressWarnings("unchecked")
    public <T> T ejecutarFuncion(String catalogName, String functionName,
            Class<T> returnType, Object[] bindValues, int[] sqlTypes) {

        String fullName = catalogName + "." + functionName;
        log.info("Ejecutando función: {} con tipo de retorno: {}", fullName, returnType.getSimpleName());

        // Oracle BOOLEAN no es compatible con JDBC estándar → ruta especial vía
        // Hibernate Session
        if (Boolean.class.equals(returnType)) {
            return (T) ejecutarFuncionBooleanOracleNative(fullName, bindValues, sqlTypes);
        }

        // Ruta estándar JPA para cualquier otro tipo de retorno
        try {
            StoredProcedureQuery query = em.createStoredProcedureQuery(fullName);
            query.registerStoredProcedureParameter(1, returnType, ParameterMode.OUT);
            for (int i = 0; i < bindValues.length; i++) {
                query.registerStoredProcedureParameter(i + 2, bindValues[i] != null
                        ? bindValues[i].getClass()
                        : Object.class, ParameterMode.IN);
                query.setParameter(i + 2, bindValues[i]);
            }
            query.execute();
            T result = (T) query.getOutputParameterValue(1);
            log.info("Función {} retornó: {}", fullName, result);
            return result;
        } catch (Exception e) {
            log.error("Error ejecutando función {}: {}", fullName, e.getMessage(), e);
            throw new RuntimeException("Error ejecutando " + fullName + ": " + e.getMessage(), e);
        }
    }

    /**
     * Ruta interna para funciones Oracle que retornan BOOLEAN nativo de PL/SQL.
     * Usa Hibernate Session + CallableStatement con JDBC puro.
     */
    private Boolean ejecutarFuncionBooleanOracleNative(String fullName,
            Object[] bindValues, int[] sqlTypes) {

        // Construye: { ? = call PKGCONTARBO.fun_ValidaBodega(?,?,?,?) }
        StringBuilder placeholders = new StringBuilder();
        for (int i = 0; i < bindValues.length; i++) {
            placeholders.append(i == 0 ? "?" : ",?");
        }
        String callSql = "{ ? = call " + fullName + "(" + placeholders + ") }";

        Session session = em.unwrap(Session.class);
        return session.doReturningWork(connection -> {
            try (CallableStatement cs = connection.prepareCall(callSql)) {
                cs.registerOutParameter(1, Types.INTEGER); // BOOLEAN → INTEGER en Oracle JDBC
                for (int i = 0; i < bindValues.length; i++) {
                    if (bindValues[i] == null) {
                        cs.setNull(i + 2, sqlTypes[i]);
                    } else {
                        cs.setObject(i + 2, bindValues[i], sqlTypes[i]);
                    }
                }
                cs.execute();
                int result = cs.getInt(1);
                log.info("Función {} retornó Boolean: {}", fullName, result == 1);
                return result == 1; // 1 = TRUE, 0 = FALSE
            }
        });
    }
}
