package com.finte.sigapp.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

/**
 * Detalle completo de una requisicion: cabecera, lineas y firmas.
 *
 * {@code estado} es derivado de las lineas, no una columna de REQUSUMI: la
 * cabecera se queda siempre en 'ac' en el ERP. Manda la fase mas atrasada que
 * quede en alguna linea, que es lo que el funcionario tiene que atender.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RequisicionDetailResponse {

    private String empresa;
    private String tipoDocumento;
    private BigDecimal numero;

    /** in, ap, en, rg o ae. Derivado de las lineas. */
    private String estado;

    private LocalDate fecha;
    private LocalDate fechaRequerida;
    private String observacion;
    private String bodega;
    private String bodegaDestino;
    private String centroInformacion;
    private String tercero;

    private List<RequisicionLineaResponse> lineas;

    /** Las dos firmas, salida primero. Siempre vienen las dos si ya se entrego. */
    private List<RequisicionFirmaResponse> firmas;
}
