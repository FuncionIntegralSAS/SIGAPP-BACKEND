package com.finte.sigapp.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Una de las dos firmas de la requisicion, dentro del detalle.
 *
 * Las firmas viven completas aqui y no se reparten en campos sueltos de la
 * cabecera: cada dato en un solo sitio.
 *
 * {@code firma} lleva la imagen en base64. Es el unico dato que se duplica a
 * proposito con el endpoint que la sirve como imagen, y se gana el sitio porque
 * ahorra al movil dos peticiones extra al abrir el detalle.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RequisicionFirmaResponse {

    /** 1 salida, 2 recibe. */
    private Integer posicion;

    /** SA o RE, que es lo que aceptan los endpoints de firma. */
    private String tipo;

    private String persona;

    /** Instante exacto de la firma. Nulo mientras esa parte no haya firmado. */
    private LocalDateTime fechaFirma;

    private boolean firmada;

    /** Imagen en base64. Nula si aun no se ha firmado. */
    private String firma;
}
