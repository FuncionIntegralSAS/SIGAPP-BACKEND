package com.finte.sigapp.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Un articulo de un traspaso, tal como se devuelve en el detalle.
 *
 * No expone MOTRIDMT, la clave tecnica de la fila: ningun endpoint la acepta y
 * nada la referencia. Ademas se confunde con facilidad con el numero de tramite,
 * porque mientras los traspasos tengan un solo articulo las dos secuencias
 * avanzan a la par y los valores coinciden.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TransferItemResponse {

    private String articulo;
    private String placa;
}
