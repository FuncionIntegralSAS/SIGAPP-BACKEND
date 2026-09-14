package com.finte.sigapp.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * Clave compuesta de {@link PlacaRequisicionModel}. Todas las columnas de
 * ASPLMORS forman la llave: una placa concreta asignada a una linea concreta.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PlacaRequisicionId implements Serializable {

    private String empresa;
    private String tipoDocumento;
    private BigDecimal numero;
    private BigDecimal secuencia;
    private String articulo;
    private String placa;
}
