package com.finte.sigapp.dto.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class PendienteArticuloResponse {
    private Long numeroConteo; // id conteo
    private String codigoQr; // código QR
    private Long cantidadContada; // cantidad contada
    private String estado; // estado
    private String idBodega; // id bodega
    private Long idArticulo; // id artículo
    private Long idUsuario; // id usuario
    private String descripcion;
}
