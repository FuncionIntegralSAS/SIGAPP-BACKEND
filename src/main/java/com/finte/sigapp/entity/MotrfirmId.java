package com.finte.sigapp.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

/**
 * Clave compuesta de {@link MotrfirmEntity}: numero de tramite mas posicion.
 *
 * Los nombres de los campos deben coincidir con los de la entidad, que es como
 * {@code @IdClass} los empareja.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MotrfirmId implements Serializable {

    private Long mofinutr;
    private Integer mofiposi;
}
