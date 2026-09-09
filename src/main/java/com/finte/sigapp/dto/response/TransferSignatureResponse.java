package com.finte.sigapp.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Una de las dos firmas de un traspaso: posicion, estado e imagen.
 *
 * No lleva la persona: la cabecera del detalle ya trae personaFuente y
 * personaDestino, y la posicion dice a cual corresponde cada fila.
 *
 * Las dos filas existen desde que se abre el tramite, con {@code firmada} en
 * false. No hay orden entre ellas: cualquiera puede llegar primero.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransferSignatureResponse {

    /** 1 entrega (fuente), 2 recibe (destino). */
    private Integer posicion;

    /** "FU" o "DE", que es como las nombran los endpoints. */
    private String tipo;

    /** Nula mientras esa parte no haya firmado. */
    private LocalDateTime fechaFirma;

    private boolean firmada;

    /**
     * Imagen PNG/JPEG en base64. Null mientras no se haya firmado.
     *
     * Viaja en el detalle y no solo en
     * {@code GET /api/v1/traspasos/sign/{id}/{tipo}} para que el front pinte la
     * pantalla con una sola peticion: el base64 entra directo en un
     * {@code <img src="data:image/png;base64,...">}, sin el fetch con header ni el
     * object URL que exigiria traerla del endpoint de imagen, que esta
     * autenticado.
     *
     * El coste son unos 110 KB con dos firmas de canvas tipicas. Ese endpoint
     * sigue existiendo para verla como imagen desde el navegador o desde soporte.
     */
    private String firma;
}
