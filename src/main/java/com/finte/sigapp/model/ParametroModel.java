package com.finte.sigapp.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Un parametro general del ERP (PARAOPER): su codigo y su valor.
 *
 * Mapeo parcial y de solo lectura. PARAOPER es una tabla del ERP: desde aqui no
 * se inserta ni se actualiza, los parametros se cargan con los scripts de
 * {@code db/}.
 *
 * Solo ve el valor GENERAL. El ERP permite sobreescribir un parametro por
 * empresa (PAOPEMPR) y por usuario (PAOPSECC), y esa resolucion la hace
 * FunBuscPara. Para parametros que dependan de eso hay que usar la funcion; este
 * modelo es para los que valen igual para toda la instalacion.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "SRF_PARAOPER")
public class ParametroModel {

    @Id
    @Column(name = "PARACODI")
    private String codigo;

    @Column(name = "PARACOVA")
    private String valor;
}
