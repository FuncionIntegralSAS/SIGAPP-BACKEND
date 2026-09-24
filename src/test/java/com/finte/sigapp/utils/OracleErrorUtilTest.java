package com.finte.sigapp.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("OracleErrorUtil")
class OracleErrorUtilTest {

    @Test
    @DisplayName("extrae el mensaje de negocio y descarta la pila de PL/SQL")
    void extraeMensajeDeNegocio() {
        RuntimeException error = new RuntimeException(
                "Error ejecutando PKG_FI_REQUISICION.pro_entregar_linea: "
                        + "ORA-20008: La Cantidad Solicitada [10] Supera la Disponible [4] en la Bodega [0101] "
                        + "ORA-06512: at \"SRFPNACUA.PKG_FI_REQUISICION\", line 412 "
                        + "ORA-06512: at line 1");

        assertThat(OracleErrorUtil.mensajeNegocio(error))
                .isEqualTo("La Cantidad Solicitada [10] Supera la Disponible [4] en la Bodega [0101]")
                .doesNotContain("ORA-");
    }

    @Test
    @DisplayName("busca el codigo en la cadena de causas, no solo en la excepcion de arriba")
    void buscaEnLasCausas() {
        Exception raiz = new Exception("ORA-20008: El tipo de requisicion [199] no tiene equivalencia");
        RuntimeException envoltura = new RuntimeException("could not execute statement", raiz);

        assertThat(OracleErrorUtil.mensajeNegocio(envoltura))
                .isEqualTo("El tipo de requisicion [199] no tiene equivalencia");
    }

    @Test
    @DisplayName("colapsa los saltos de linea que PL/SQL introduce al concatenar")
    void colapsaEspacios() {
        RuntimeException error = new RuntimeException("ORA-20008: Faltan placas\n   por asignar   al articulo");

        assertThat(OracleErrorUtil.mensajeNegocio(error)).isEqualTo("Faltan placas por asignar al articulo");
    }

    /**
     * Mensaje literal que devolvio recibir el traspaso 11 el 2026-09-24: la
     * ubicacion de pkgerror delante, la pila en espanol, el enlace de ayuda de
     * Oracle 23 y el statement de Hikari detras.
     */
    @Test
    @DisplayName("quita la ubicacion de pkgerror, la pila y lo que anade el driver")
    void quitaUbicacionDePkgerror() {
        RuntimeException error = new RuntimeException(
                "Error ejecutando PKG_FI_MOVITRAS.pro_recibir: Error calling CallableStatement.getMoreResults "
                        + "[ORA-20008: package body SRFPNACUA.PKG_FI_MOVITRAS.PRO_RECIBIR.0      121 |"
                        + "Bodega Destino [2612] o Bodega Fuente [F571] Deben ser de Tipo Personal\n"
                        + "ORA-06512: en \"SRFPNACUA.PKG_FI_MOVITRAS\", línea 1215\n"
                        + "ORA-06512: en línea 1\n\r\n"
                        + "https://docs.oracle.com/error-help/db/ora-20008/] "
                        + "[HikariProxyCallableStatement@934536653 wrapping "
                        + "oracle.jdbc.driver.OracleCallableStatementWrapper@349984b1]");

        assertThat(OracleErrorUtil.mensajeNegocio(error))
                .isEqualTo("Bodega Destino [2612] o Bodega Fuente [F571] Deben ser de Tipo Personal");
    }

    @Test
    @DisplayName("corta el enlace de ayuda aunque no le siga ningun ORA-")
    void cortaEnlaceDeAyudaSinPila() {
        RuntimeException error = new RuntimeException(
                "[ORA-20008: procedure SRFPNACUA.PRO_X.0 7 |El traspaso [11] ya fue recibido\n"
                        + "https://docs.oracle.com/error-help/db/ora-20008/] [HikariProxyCallableStatement@1]");

        assertThat(OracleErrorUtil.mensajeNegocio(error)).isEqualTo("El traspaso [11] ya fue recibido");
    }

    @Test
    @DisplayName("no toca un mensaje que solo contiene una barra vertical")
    void conservaBarraQueNoEsUbicacion() {
        RuntimeException error = new RuntimeException("ORA-20008: Centro A | Centro B no coinciden");

        assertThat(OracleErrorUtil.mensajeNegocio(error)).isEqualTo("Centro A | Centro B no coinciden");
    }

    @Test
    @DisplayName("devuelve el mensaje original cuando no hay codigo de negocio")
    void sinCodigoDevuelveElOriginal() {
        RuntimeException error = new RuntimeException("Connection timed out");

        assertThat(OracleErrorUtil.mensajeNegocio(error)).isEqualTo("Connection timed out");
    }

    @Test
    @DisplayName("no falla con una excepcion sin mensaje")
    void toleraMensajeNulo() {
        assertThat(OracleErrorUtil.mensajeNegocio(new RuntimeException())).isEqualTo("Error desconocido");
    }
}
