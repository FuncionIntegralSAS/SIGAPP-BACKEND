package com.finte.sigapp.utils;

import jakarta.persistence.EntityManager;
import jakarta.persistence.ParameterMode;
import jakarta.persistence.Query;
import jakarta.persistence.StoredProcedureQuery;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.hibernate.Session;
import org.springframework.stereotype.Component;

import java.sql.CallableStatement;
import java.sql.Types;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Utilitario genérico para ejecutar procedimientos y funciones almacenadas en
 * Oracle (PL/SQL).
 *
 * - Procedimientos (void) → usa JPA StoredProcedureQuery.
 * - Funciones con retorno <T> → usa JPA para tipos estándar (String,
 * Integer,Long...),
 * y Hibernate Session + CallableStatement para Boolean,
 * dado que JDBC/JPA no soportan nativamente el BOOLEAN de Oracle PL/SQL.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ProcedureExecutor {

    private final EntityManager em;

    /**
     * Ejecuta un procedimiento almacenado en Oracle que no retorna valor (void).
     *
     * @param procedureName nombre del procedimiento (ej:
     *                      "PKGCONTARBO.proGeneContFisi")
     * @param params        parámetros de entrada: cada List<ProcedureParam>
     *                      contiene {nombre,
     *                      valor, Class<?>, ParameterMode.IN/OUT}
     */
    public void ejecutarProcedimiento(String procedureName, List<ProcedureParam> params) {
        log.info("Ejecutando procedimiento: {}", procedureName);
        try {
            StoredProcedureQuery query = em.createStoredProcedureQuery(procedureName);
            configurarParametros(query, params);
            query.execute();
            log.info("Procedimiento {} ejecutado correctamente", procedureName);
        } catch (Exception e) {
            log.error("Error ejecutando procedimiento {}: {}", procedureName, e.getMessage(), e);
            throw new RuntimeException("Error ejecutando " + procedureName + ": " + e.getMessage(), e);
        }
    }

    /**
     * Ejecuta una función PL/SQL de Oracle y retorna un valor del tipo genérico
     * 
     * @param functionName nombre de la función (ej: "fun_ValidaBodega")
     * @param returnType   tipo de retorno esperado (ej: Boolean.class,String.class,
     *                     Integer.class)
     * @param params       cada List<ProcedureParam> contiene {nombre,valor,
     *                     Class<?>, ParameterMode.IN/OUT}
     * @return el valor retornado por la función casteado al tipo generico.
     */
    @SuppressWarnings("unchecked")
    public <T> T ejecutarFuncion(String functionName, Class<T> returnType, List<ProcedureParam> params) {
        log.info("Ejecutando función: {} con tipo de retorno: {}", functionName, returnType.getSimpleName());

        // Manejo especial para BOOLEAN de Oracle
        if (Boolean.class.equals(returnType)) {
            return (T) ejecutarFuncionBooleanOracleNative(functionName, params);
        }

        // Ruta estándar JPA para otros tipos
        try {
            StoredProcedureQuery query = em.createStoredProcedureQuery(functionName);
            // El primer parámetro en una función JPA suele ser el OUT (return)
            query.registerStoredProcedureParameter(1, returnType, ParameterMode.OUT);

            // Registramos los demas parametros desplazando el indice (+1)
            for (int i = 0; i < params.size(); i++) {
                ProcedureParam p = params.get(i);
                query.registerStoredProcedureParameter(i + 2, p.getJavaType(), p.getMode());
                query.setParameter(i + 2, p.getValue());
            }

            query.execute();
            return (T) query.getOutputParameterValue(1);
        } catch (Exception e) {
            log.error("Error ejecutando función JPA {}: {}", functionName, e.getMessage());
            throw new RuntimeException("Error en " + functionName + ": " + e.getMessage());
        }
    }

    /**
     * Ruta interna para funciones Oracle que retornan BOOLEAN nativo de PL/SQL.
     * Usa Hibernate Session + CallableStatement con JDBC puro.
     */
    private Boolean ejecutarFuncionBooleanOracleNative(String fullName, List<ProcedureParam> params) {

        StringBuilder sb = new StringBuilder();
        sb.append("begin ");
        sb.append("  if " + fullName + "(");

        // Construir los placeholders: ?, ?, ?
        for (int i = 0; i < params.size(); i++) {
            sb.append(i == 0 ? "?" : ", ?");
        }

        // Si es true devuelve 1, si es false devuelve 0
        sb.append(") then ? := 1; else ? := 0; end if; ");
        sb.append("end;");

        String callSql = sb.toString();
        Session session = em.unwrap(Session.class);

        return session.doReturningWork(connection -> {
            try (CallableStatement cs = connection.prepareCall(callSql)) {
                int totalParams = params.size();

                // 1. Seteamos los parámetros de entrada de la función (inician en 1)
                for (int i = 0; i < totalParams; i++) {
                    ProcedureParam p = params.get(i);
                    cs.setObject(i + 1, p.getValue());
                }

                // 2. Registramos los dos placeholders finales que pusimos en el bloque (el ? :=
                // 1 y ? := 0)
                // Ambos apuntan a la misma lógica de salida, pero por simplicidad usaremos dos
                // índices
                int outputIndex1 = totalParams + 1;
                int outputIndex2 = totalParams + 2;
                cs.registerOutParameter(outputIndex1, java.sql.Types.INTEGER);
                cs.registerOutParameter(outputIndex2, java.sql.Types.INTEGER);

                cs.execute();

                // Recuperamos el valor (si entró al THEN, el primero tendrá valor)
                int result = cs.getInt(outputIndex1);
                return result == 1;
            }
        });
    }

    public void ejecutarBloqueAnonimo(String bloquePlsql,
            Map<String, Object> params) {
        log.info("init bloque PLSQL");
        try {
            Query query = em.createNativeQuery(bloquePlsql);

            params.forEach(query::setParameter);

            query.executeUpdate();

            log.info("Bloque PL/SQL ejecutado correctamente");

        } catch (Exception e) {
            log.error("Error ejecutnado bloque PL/SQL: {}", e.getMessage(), e);
            throw new RuntimeException("Error ejecutando bloque PL/SQL", e);
        }
    }

    public Map<String, Object> ejecutarBloqueConSalida(String bloquePlsql,
            List<ProcedureParam> paramsIn,
            List<ProcedureParam> paramsOut) {

        log.info("iniciando bloque PLSQL con parametros OUT");

        Map<String, Object> resultadoSalida = new HashMap<>();
        Session session = em.unwrap(Session.class);

        return session.doReturningWork(connection -> {
            try (CallableStatement cs = connection.prepareCall(bloquePlsql)) {
                int index = 1;
                /* Registrar parametros IN */
                if (paramsIn != null) {
                    for (ProcedureParam param : paramsIn) {
                        cs.setObject(index++, param.getValue());
                    }
                }

                int outStartIndex = index;
                if (paramsOut != null) {
                    for (ProcedureParam param : paramsOut) {
                        cs.registerOutParameter(index++, getSqlType(param.getJavaType()));
                    }
                }

                cs.execute();

                if (paramsOut != null) {
                    int outIndex = outStartIndex;
                    for (ProcedureParam param : paramsOut) {
                        Object value = cs.getObject(outIndex++);
                        resultadoSalida.put(param.getName(), value);
                        log.debug("Parámetro OUT '{}' = {}", param.getName(), value);
                    }
                }
                log.info("Bloque PLSQL ejecutado correctamente. Salidas: {}", resultadoSalida);
                return resultadoSalida;
            } catch (Exception e) {
                log.error("Error ejecutando bloque PLSQL con salida: {}", e.getMessage());
                throw new RuntimeException("Error ejecutando bloque PLSQL con salida: " + e.getMessage());
            }
        });
    }

    private void configurarParametros(StoredProcedureQuery query, List<ProcedureParam> params) {
        for (ProcedureParam param : params) {
            query.registerStoredProcedureParameter(param.getName(), param.getJavaType(), param.getMode());
            query.setParameter(param.getName(), param.getValue());
        }
    }

    private int getSqlType(Class<?> javaType) {
        if (javaType == String.class)
            return Types.VARCHAR;
        if (javaType == Integer.class || javaType == int.class)
            return Types.INTEGER;
        if (javaType == Long.class || javaType == long.class)
            return Types.NUMERIC;
        if (javaType == Boolean.class || javaType == boolean.class)
            return Types.INTEGER; // Oracle no tiene BOOLEAN JDBC
        if (javaType == java.util.Date.class)
            return Types.DATE;
        if (javaType == java.math.BigDecimal.class)
            return Types.NUMERIC;
        return Types.VARCHAR;
    }
}
