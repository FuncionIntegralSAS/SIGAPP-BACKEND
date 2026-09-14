package com.finte.sigapp.utils;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Extrae el mensaje de negocio de un error que viene del ERP.
 *
 * Los paquetes del sistema financiero comunican los errores de negocio con
 * {@code raise_application_error(-20008, ...)}: "no hay saldo suficiente",
 * "faltan placas por asignar", "el tipo no tiene equivalencia". Ese texto es
 * exactamente lo que el usuario necesita leer.
 *
 * El problema es como llega. ProcedureExecutor envuelve la excepcion en una
 * RuntimeException, el driver antepone su propia cadena y por encima se apilan
 * las trazas de Hibernate, de modo que el mensaje util queda sepultado en algo
 * como:
 *
 * <pre>
 *   Error ejecutando PKG_FI_REQUISICION.pro_entregar_linea: ... ORA-20008: La
 *   Cantidad Solicitada [10] Supera la Disponible [4] ... ORA-06512: at line 1
 * </pre>
 *
 * Esta clase recorta lo que va detras del codigo y corta antes del ORA-06512
 * -que es la pila de PL/SQL y no le dice nada a quien esta en el almacen-. Si
 * no encuentra el patron devuelve el mensaje original: es preferible un texto
 * feo a uno vacio.
 */
public final class OracleErrorUtil {

    /**
     * Codigo con el que el ERP marca sus errores de negocio. El texto util es
     * todo lo que va detras, hasta el siguiente ORA- o el final.
     */
    private static final Pattern ERROR_NEGOCIO =
            Pattern.compile("ORA-20\\d{3}:\\s*(.+?)(?=\\s*ORA-\\d{5}|$)", Pattern.DOTALL);

    private OracleErrorUtil() {
    }

    /**
     * Devuelve el mensaje de negocio del ERP, o el mensaje original si la
     * excepcion no trae ninguno.
     *
     * Recorre la cadena de causas porque el ORA- rara vez esta en la excepcion
     * de mas arriba.
     */
    public static String mensajeNegocio(Throwable error) {
        Throwable actual = error;

        while (actual != null) {
            String mensaje = actual.getMessage();

            if (mensaje != null) {
                Matcher matcher = ERROR_NEGOCIO.matcher(mensaje);

                if (matcher.find()) {
                    return limpiar(matcher.group(1));
                }
            }

            actual = actual.getCause();
        }

        return error != null && error.getMessage() != null ? error.getMessage() : "Error desconocido";
    }

    /** Colapsa saltos de linea y espacios repetidos, que PL/SQL introduce al concatenar. */
    private static String limpiar(String mensaje) {
        return mensaje.replaceAll("\\s+", " ").trim();
    }
}
