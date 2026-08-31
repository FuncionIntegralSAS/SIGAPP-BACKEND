package com.finte.sigapp.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Vista de bandeja de los traspasos (tabla FI_MOVITRAS).
 *
 * Mapea las columnas propias de FI_MOVITRAS mas las descripciones resueltas por
 * subconsulta (NOMBELEM, PERSFUEN, BODEFUEN, PERSDEST, BODEDEST), que no existen
 * fisicamente en la tabla.
 *
 * Por esa razon es de solo lectura y solo debe consultarse con la query nativa
 * de {@link com.finte.sigapp.repository.TransferJpaRepository}: los metodos
 * derivados de JpaRepository generarian SQL con las columnas de subconsulta y
 * fallarian en Oracle. Para acceso por clave usar
 * {@link com.finte.sigapp.entity.MovitrasEntity}.
 *
 * No incluye las firmas (MOTRFIFU / MOTRFIDE): son CLOB y la bandeja solo
 * necesita saber si ya fueron registradas, cosa que se deduce de las fechas de
 * aceptacion.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "FI_MOVITRAS")
public class MovitrasTraspasoModel {

    @Id
    @Column(name = "MOTRIDMT")
    private Long motridmt; // Identificador del traspaso

    @Column(name = "MOTRDREM")
    private String motrdrem; // Empresa del documento

    @Column(name = "MOTRDRTD")
    private String motrdrtd; // Tipo de documento

    @Column(name = "MOTRDRND")
    private BigDecimal motrdrnd; // Numero del documento

    @Column(name = "MOTRPEFU")
    private String motrpefu; // Codigo persona fuente

    @Column(name = "MOTRPEDE")
    private String motrpede; // Codigo persona destino

    @Column(name = "MOTRARTI")
    private String motrarti; // Codigo del elemento

    @Column(name = "MOTRPLAC")
    private String motrplac; // Placa del elemento

    @Column(name = "MOTRESTA")
    private String motresta; // pe | ap | na | af | ad | re

    @Column(name = "MOTROBSE")
    private String motrobse; // Observacion / motivo del traspaso

    @Column(name = "MOTRMORE")
    private String motrmore; // Motivo de rechazo

    @Column(name = "MOTRFECR")
    private LocalDateTime motrfecr; // Fecha de creacion

    @Column(name = "MOTRUSCR")
    private String motruscr; // Usuario que crea

    @Column(name = "MOTRFEAP")
    private LocalDateTime motrfeap; // Fecha de aprobacion

    @Column(name = "MOTRUSAP")
    private String motrusap; // Usuario que aprueba

    @Column(name = "MOTRFAFU")
    private LocalDateTime motrfafu; // Fecha de aceptacion de la fuente

    @Column(name = "MOTRFADE")
    private LocalDateTime motrfade; // Fecha de aceptacion del destino

    @Column(name = "MOTRFERE")
    private LocalDateTime motrfere; // Fecha en que el destino recibe

    /* Descripciones resueltas por subconsulta */

    @Column(name = "NOMBELEM")
    private String nombElem; // Descripcion del activo (ACTIFIJO.ACFIOBSE)

    @Column(name = "PERSFUEN")
    private String persFuen; // PERSCODI - PERSNOMB PERSAPEL de la persona fuente

    @Column(name = "BODEFUEN")
    private String bodeFuen; // BODECODI - BODEDESC de la bodega del activo

    @Column(name = "PERSDEST")
    private String persDest; // PERSCODI - PERSNOMB PERSAPEL de la persona destino

    @Column(name = "BODEDEST")
    private String bodeDest; // BODECODI - BODEDESC de la bodega destino
}
