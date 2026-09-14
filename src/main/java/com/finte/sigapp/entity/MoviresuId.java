package com.finte.sigapp.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * Clave compuesta de {@link MoviresuEntity}. Son las seis columnas de la PK de
 * MOVIRESU en el ERP.
 *
 * Las tres primeras identifican la requisicion y las tres ultimas la linea
 * dentro de ella: el mismo articulo puede aparecer mas de una vez si va en otra
 * bodega o con otra secuencia, asi que ninguna de las tres sobra.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class MoviresuId implements Serializable {

    private String morstdem;
    private String morstido;
    private BigDecimal morsrenu;
    private String morsarti;
    private String morsbode;
    private BigDecimal morssecu;
}
