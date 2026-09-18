package com.finte.sigapp.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Cabecera de una requisicion para el detalle (tabla REQUSUMI del ERP).
 *
 * Mapeo parcial: solo las columnas que la pantalla necesita. No se incluye
 * RESUESTA porque no significa nada aqui -se queda en {@code ac} siempre- y el
 * estado que el usuario ve se deriva de las lineas.
 *
 * Solo lectura. Este modulo no escribe nunca en REQUSUMI.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@IdClass(RequisicionId.class)
@Table(name = "REQUSUMI")
public class RequisicionCabeceraModel {

    @Id
    @Column(name = "RESUTDEM")
    private String empresa;

    @Id
    @Column(name = "RESUTIDO")
    private String tipoDocumento;

    @Id
    @Column(name = "RESUNUME")
    private BigDecimal numero;

    @Column(name = "RESUFECH")
    private LocalDate fecha;

    @Column(name = "RESUFERE")
    private LocalDate fechaRequerida;

    @Column(name = "RESUOBSE")
    private String observacion;

    @Column(name = "RESUDIVI")
    private String division;

    @Column(name = "RESUCIUD")
    private String ciudad;

    @Column(name = "RESUBODE")
    private String bodega;

    @Column(name = "RESUBODS")
    private String bodegaDestino;

    @Column(name = "RESUCEIN")
    private String centroInformacion;

    @Column(name = "RESUTERC")
    private String tercero;

    /** BODERESP de la bodega fuente. No es columna de REQUSUMI: viene del JOIN. */
    @Column(name = "RESPBODE")
    private String responsableBodega;

    /** BODERESP de la bodega destino. No es columna de REQUSUMI: viene del JOIN. */
    @Column(name = "RESPBODS")
    private String responsableBodegaDestino;
}
