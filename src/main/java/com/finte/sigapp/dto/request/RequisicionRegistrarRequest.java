package com.finte.sigapp.dto.request;

import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Cuerpo del registro de la salida.
 *
 * Las lineas son OPCIONALES y solo hay que enviarlas para recibir menos de lo
 * entregado. Lo que no venga se recibe completo, que es el caso normal: quien
 * firma conforme esta aceptando lo que se le entrego.
 *
 * Recibir parcial tiene una consecuencia que conviene conocer: lo entregado y
 * no recibido no vuelve al inventario ni queda como anulado. La linea se
 * registra habiendo sacado del stock solo lo recibido.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RequisicionRegistrarRequest {

    @Valid
    private List<RequisicionLineaRequest> lineas;
}
