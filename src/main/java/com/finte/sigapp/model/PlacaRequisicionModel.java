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

/**
 * Placa asignada a una linea de requisicion (tabla ASPLMORS del ERP).
 *
 * Solo aplica a articulos devolutivos. El ERP exige que el numero de placas
 * coincida con la cantidad que se mueve: al entregar contrasta contra la
 * cantidad entregada y al sellar la recibida contra esa. Por eso el detalle las
 * expone: sin ellas el registro falla despues de haber recogido las dos firmas,
 * que es el peor momento para descubrirlo.
 *
 * Solo lectura desde JPA. Asignar y soltar placas lo hace el paquete.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@IdClass(PlacaRequisicionId.class)
@Table(name = "SRF_ASPLMORS")
public class PlacaRequisicionModel {

    @Id
    @Column(name = "ASPLTDEM")
    private String empresa;

    @Id
    @Column(name = "ASPLTIDO")
    private String tipoDocumento;

    @Id
    @Column(name = "ASPLNUME")
    private BigDecimal numero;

    @Id
    @Column(name = "ASPLSECU")
    private BigDecimal secuencia;

    @Id
    @Column(name = "ASPLARTI")
    private String articulo;

    @Id
    @Column(name = "ASPLPLAC")
    private String placa;
}
