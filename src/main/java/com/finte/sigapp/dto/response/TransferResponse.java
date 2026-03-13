package com.finte.sigapp.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class TransferResponse {
    private Long id;
    private String articuloID;
    private String placaArticulo;
    private String nombreActivo;
    private String personaFuente;
    private String personaDestino;
    private String bodegaFuente;
    private String bodegaDestino;
    private String descripcionTramite;
    private LocalDateTime fechaCreacion;
    private String estado;
    private LocalDateTime fechaActualizacion;
    private String motivoRechazo;
}