package com.finte.sigapp.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Fila de la bandeja de traspasos: UN TRAMITE, no un articulo.
 *
 * Lo minimo para listar y enlazar. Todo lo demas -articulos, personas, estado
 * detallado, observaciones, auditoria y firmas- lo sirve
 * {@code GET /api/v1/traspasos/get/{id}} en {@link TransferDetailResponse}.
 *
 * OJO CON LOS DOS NUMEROS. Son de secuencias distintas y no significan lo mismo:
 *
 * <ul>
 *   <li>{@code id} es MOTRNUTR, el numero de tramite de SIGAPP. Es el valor que
 *       va en {@code {id}} de todas las rutas del modulo.</li>
 *   <li>{@code numeroDocumento} es REQUSUMI.RESUNUME, el numero de la requisicion
 *       en el ERP. Es el que el usuario reconoce, pero no sirve para llamar a la
 *       API: la llave del documento son tres columnas.</li>
 * </ul>
 */
@Data
@Builder
public class TransferResponse {

    /** Numero de tramite (MOTRNUTR). Es el id con el que operan los endpoints. */
    private Long id;

    /* Referencia del documento en el ERP, que es como el usuario lo reconoce */
    private String tipoDocumento;
    private BigDecimal numeroDocumento;

    private LocalDateTime fechaCreacion;
}
