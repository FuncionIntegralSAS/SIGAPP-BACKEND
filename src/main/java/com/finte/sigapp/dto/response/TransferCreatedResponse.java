package com.finte.sigapp.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Resultado de crear un traspaso.
 *
 * Ademas del id del tramite devuelve la llave del documento REQUSUMI que el
 * paquete genero: el tipo y el numero los asigna la base de datos (el numero lo
 * pone el trigger TRGBINR_REQUSUMI), asi que el cliente no tiene forma de
 * conocerlos si no se los devolvemos aqui.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransferCreatedResponse {

    private Long id;
    private String empresaDocumento;
    private String tipoDocumento;
    private BigDecimal numeroDocumento;
}
