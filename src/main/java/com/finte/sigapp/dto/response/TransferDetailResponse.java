package com.finte.sigapp.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Detalle de un traspaso, incluidas las firmas en base64.
 *
 * Se separa de {@link TransferResponse} para que la bandeja no arrastre los dos
 * CLOB de firma en cada fila.
 */
@Data
@Builder
public class TransferDetailResponse {

    private Long id;
    private String estado;

    /* Documento de requisicion (REQUSUMI) */
    private String empresaDocumento;
    private String tipoDocumento;
    private BigDecimal numeroDocumento;

    private String elemento;
    private String placaElemento;

    private String personaFuente;
    private String personaDestino;

    private String observacion;
    private String motivoRechazo;

    private LocalDateTime fechaCreacion;
    private String usuarioCreacion;
    private LocalDateTime fechaAprobacion;
    private String usuarioAprobacion;
    private LocalDateTime fechaAceptacionFuente;
    private LocalDateTime fechaAceptacionDestino;
    private LocalDateTime fechaRecibe;

    /** Imagen PNG en base64. Null mientras la parte no haya firmado. */
    private String firmaFuente;
    private String firmaDestino;
}
