package com.finte.sigapp.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * Clave compuesta de {@link RequfirmEntity}: la terna que identifica la
 * requisicion mas la posicion de la firma.
 *
 * Son cuatro columnas y no una porque la requisicion no tiene identificador
 * propio: la identifican empresa, tipo de documento y numero, que es la llave
 * de REQUSUMI.
 *
 * Los nombres de los campos deben coincidir con los de la entidad, que es como
 * {@code @IdClass} los empareja.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RequfirmId implements Serializable {

    private String refiempr;
    private String refitido;
    private BigDecimal refinume;
    private Integer refiposi;
}
