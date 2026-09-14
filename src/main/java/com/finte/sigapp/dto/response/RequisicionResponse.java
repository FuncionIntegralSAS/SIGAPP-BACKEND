package com.finte.sigapp.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Una fila de la bandeja de requisiciones.
 *
 * Lleva lo justo para listar y enlazar: la terna que identifica el documento y
 * lo que permite reconocerlo de un vistazo. Todo lo demas esta en el detalle y
 * no se repite aqui.
 *
 * {@code lineas} da el tamano del trabajo, que es lo que decide si el
 * funcionario abre esta requisicion o la siguiente.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RequisicionResponse {

    private String empresa;
    private String tipoDocumento;
    private BigDecimal numero;
    private String estado;
    private LocalDate fecha;
    private String bodega;
    private Integer lineas;
}
