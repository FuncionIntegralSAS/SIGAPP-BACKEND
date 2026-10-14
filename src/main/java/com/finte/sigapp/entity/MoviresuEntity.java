package com.finte.sigapp.entity;

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
 * Linea de una requisicion de suministro (tabla MOVIRESU del ERP).
 *
 * Una fila por articulo, bodega y secuencia. Aqui vive el estado real del
 * tramite: la cabecera REQUSUMI se queda siempre en {@code ac} y el estado de
 * la requisicion es una funcion de sus lineas.
 *
 * <pre>
 *   in -> ap -> en -> rg        camino normal
 *               \
 *                -> ae          anulado al entregar
 * </pre>
 *
 * Las cinco cantidades no son intercambiables. La que mueve inventario es
 * {@code morscaen}: es la que {@code PKGFNMOVIRESU.PROSALIDA} lleva al
 * movimiento al registrar. {@code morscare} no la lee el ERP en ningun momento,
 * asi que una recepcion parcial se aplica bajando la entregada antes de generar
 * la salida: lo no recibido no llega a salir, porque ARTIBODE.ARBOCAFI se
 * descuenta al registrar y no al entregar.
 *
 * SOLO LECTURA. Toda escritura pasa por PKG_FI_REQUISICION: los UPDATE sobre
 * esta tabla disparan un trigger del ERP que valida disponibilidad, placas y
 * saldos, y hacerlos desde JPA saltaria el bloqueo del paquete.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@IdClass(MoviresuId.class)
@Table(name = "SRF_MOVIRESU")
public class MoviresuEntity {

    @Id
    @Column(name = "MORSTDEM")
    private String morstdem; // Empresa

    @Id
    @Column(name = "MORSTIDO")
    private String morstido; // Tipo de documento de la requisicion

    @Id
    @Column(name = "MORSRENU")
    private BigDecimal morsrenu; // Numero de la requisicion

    @Id
    @Column(name = "MORSARTI")
    private String morsarti; // Articulo

    @Id
    @Column(name = "MORSBODE")
    private String morsbode; // Bodega fuente

    @Id
    @Column(name = "MORSSECU")
    private BigDecimal morssecu; // Secuencia dentro de la requisicion

    @Column(name = "MORSESTA")
    private String morsesta; // in | ap | en | ae | an | rg

    @Column(name = "MORSDEAR")
    private String morsdear; // Descripcion del articulo

    @Column(name = "MORSUNID")
    private String morsunid; // Unidad de medida

    @Column(name = "MORSBODS")
    private String morsbods; // Bodega destino

    @Column(name = "MORSCASO")
    private BigDecimal morscaso; // Cantidad solicitada

    @Column(name = "MORSCAAP")
    private BigDecimal morscaap; // Cantidad aprobada

    @Column(name = "MORSCAEN")
    private BigDecimal morscaen; // Cantidad entregada

    @Column(name = "MORSCAAN")
    private BigDecimal morscaan; // Cantidad anulada

    @Column(name = "MORSCARE")
    private BigDecimal morscare; // Cantidad recibida. Auditoria: el ERP no la lee

    @Column(name = "MORSCAPE")
    private BigDecimal morscape; // Cantidad pendiente

    @Column(name = "MORSUSAP")
    private String morsusap; // Usuario que aprobo

    @Column(name = "MORSFEAP")
    private LocalDate morsfeap; // Fecha de aprobacion

    @Column(name = "MORSOBSE")
    private String morsobse; // Observacion de la linea

    @Column(name = "MORSCECO")
    private String morsceco; // Centro de costo destino

    @Column(name = "MORSCEIN")
    private String morscein; // Centro de informacion

    @Column(name = "MORSPESO")
    private String morspeso; // Persona solicitante
}
