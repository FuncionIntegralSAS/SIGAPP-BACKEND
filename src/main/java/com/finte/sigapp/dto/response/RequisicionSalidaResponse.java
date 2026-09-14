package com.finte.sigapp.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Resultado de generar la salida de inventario.
 *
 * {@code tipoSalida} es el tipo de documento que el ERP uso, resuelto por
 * equivalencia a partir del tipo de la requisicion. Se devuelve para que quede
 * en el registro de la operacion: es el dato que hay que mirar si despues hay
 * que buscar el documento en el sistema financiero.
 *
 * {@code mensaje} es el texto que el propio ERP produce durante el proceso.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RequisicionSalidaResponse {

    private String tipoSalida;
    private String mensaje;
}
