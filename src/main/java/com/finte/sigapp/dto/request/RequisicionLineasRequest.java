package com.finte.sigapp.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Cuerpo de aprobar y de entregar: las lineas sobre las que se actua, con su
 * cantidad.
 *
 * Las lineas que no vengan en la lista NO se tocan. Eso permite aprobar una
 * requisicion por partes, que es como trabaja quien tiene que consultar algo
 * antes de decidir sobre un articulo concreto.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RequisicionLineasRequest {

    @NotEmpty(message = "hay que indicar al menos una linea")
    @Valid
    private List<RequisicionLineaRequest> lineas;
}
