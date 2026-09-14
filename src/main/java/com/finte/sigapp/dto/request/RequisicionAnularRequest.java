package com.finte.sigapp.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Cuerpo de la anulacion al entregar.
 *
 * Sin cantidades: anular es total sobre el saldo aprobado de cada linea. Una
 * linea con entrega parcial no se puede anular, porque saldria del estado
 * entregado y esa entrega no llegaria nunca al inventario.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RequisicionAnularRequest {

    @NotEmpty(message = "hay que indicar al menos una linea")
    @Valid
    private List<RequisicionLineaClaveRequest> lineas;
}
