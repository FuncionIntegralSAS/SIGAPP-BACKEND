package com.finte.sigapp.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Un tipo de requisicion que la aplicacion puede gestionar.
 *
 * No expone el tipo de documento de salida al que equivale: eso es un detalle
 * de como se genera el documento, el front no lo necesita y devolverlo
 * invitaria a que alguien lo mandara de vuelta en una peticion.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RequisicionTipoResponse {

    private String codigo;
    private String nombre;
}
