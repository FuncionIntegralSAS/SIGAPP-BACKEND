package com.finte.sigapp.dto.response;

import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Detalle de un traspaso: la cabecera, sus articulos y sus dos firmas.
 *
 * Se separa de {@link TransferResponse} para que la bandeja no arrastre ni la
 * lista de articulos ni las imagenes de firma en cada fila.
 */
@Data
@Builder
public class TransferDetailResponse {

    /** Numero de tramite (MOTRNUTR). */
    private Long id;

    private String estado;

    /* Documento de requisicion (REQUSUMI) */
    private String empresaDocumento;
    private String tipoDocumento;
    private BigDecimal numeroDocumento;

    private String personaFuente;
    private String personaDestino;

    private String observacion;
    private String motivoRechazo;

    private LocalDateTime fechaCreacion;
    private String usuarioCreacion;
    private LocalDateTime fechaAprobacion;
    private String usuarioAprobacion;
    private LocalDateTime fechaRecibe;

    /** Los N articulos del tramite, en el orden en que se cargaron. */
    private List<TransferItemResponse> articulos;

    /**
     * Las dos firmas: posicion 1 entrega, posicion 2 recibe.
     *
     * Todo lo de las firmas esta aqui, incluida la imagen en base64. La cabecera
     * no repite las fechas de aceptacion ni saca las imagenes a campos sueltos:
     * era el mismo dato en dos sitios.
     */
    private List<TransferSignatureResponse> firmas;
}
