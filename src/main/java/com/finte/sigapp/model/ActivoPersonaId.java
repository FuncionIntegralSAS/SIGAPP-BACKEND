package com.finte.sigapp.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Clave compuesta de {@link ActivoPersonaModel}: articulo mas placa.
 *
 * Los nombres de los campos deben coincidir con los de la entidad, que es como
 * {@code @IdClass} los empareja.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ActivoPersonaId implements Serializable {

    private String acfiarti;
    private String acfiplac;
}
