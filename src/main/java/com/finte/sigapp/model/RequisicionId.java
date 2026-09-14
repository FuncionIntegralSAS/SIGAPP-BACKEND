package com.finte.sigapp.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

/**
 * Terna que identifica una requisicion: empresa, tipo de documento y numero.
 *
 * Es la llave de REQUSUMI y el prefijo de la de MOVIRESU. La comparten los
 * modelos de lectura del modulo, que agrupan por ella.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RequisicionId implements Serializable {

    private String empresa;
    private String tipoDocumento;
    private BigDecimal numero;
}
