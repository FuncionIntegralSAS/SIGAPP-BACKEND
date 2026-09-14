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
