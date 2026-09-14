package com.finte.sigapp.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Una linea del detalle de la requisicion.
 *
 * Los tres primeros campos son los que hay que devolver en las peticiones de
 * aprobar, entregar, anular y registrar: identifican la linea dentro del
 * documento.
 *
 * Las cinco cantidades van juntas porque cuentan la historia completa de la
 * linea y el funcionario decide mirandolas a la vez.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RequisicionLineaResponse {

    private String articulo;
    private String bodega;
    private BigDecimal secuencia;

    private String descripcion;
    private String unidad;
    private String estado;

    private BigDecimal solicitada;
    private BigDecimal aprobada;
    private BigDecimal entregada;
    private BigDecimal anulada;
    private BigDecimal recibida;
    private BigDecimal pendiente;

    private String usuarioAprueba;
    private LocalDate fechaAprobacion;

    /**
     * Placas asignadas, solo en articulos devolutivos.
     *
     * Se incluyen aunque sean un dato mas porque el ERP exige que su numero
     * coincida con la cantidad que se mueve: sin verlas, el desajuste aparece al
     * registrar, cuando las dos firmas ya se recogieron.
     */
    private List<String> placas;
}
