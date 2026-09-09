package com.finte.sigapp.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Persona asociada a una bodega, para elegir fuente o destino de un traspaso.
 *
 * {@code cedula} es el PERSCODI, que es lo que hay que enviar en
 * {@code personaFuente} o {@code personaDestino} al crear el traspaso.
 *
 * Nombre y apellido van separados para que el front los presente como quiera;
 * no se devuelve division ni estado porque la consulta ya filtra por ambos.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransferPersonResponse {

    private String cedula;
    private String nombre;
    private String apellido;
}
