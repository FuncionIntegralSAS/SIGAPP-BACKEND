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

/**
 * El dato de un activo que se convierte en CABECERA del documento de traspaso:
 * el centro de informacion.
 *
 * {@code ACFICEIN} acaba en {@code REQUSUMI.RESUCEIN}, que es una sola columna
 * para todo el documento. Si los articulos pertenecen a centros distintos no hay
 * un valor correcto que poner, y tomar el del primero mandaria el documento a un
 * centro equivocado sin que nadie se entere.
 *
 * Trae tambien la bodega del activo y su tipo, que deciden si el ERP acepta
 * moverlo: su traspaso exige que la bodega fuente sea personal.
 *
 * NO se incluye el tercero. {@code ACFITERC} es "el tercero al que se compra"
 * -el proveedor del activo- y no tiene relacion con un cambio de responsable
 * interno; el documento lleva el NIT de la propia empresa.
 *
 * CUIDADO: modelo de solo lectura. No usar los metodos derivados de
 * JpaRepository.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@IdClass(ActivoPersonaId.class)
@Table(name = "SRF_ACTIFIJO")
public class ActivoCompatibilidadModel {

    @Id
    @Column(name = "ACFIARTI")
    private String acfiarti;

    @Id
    @Column(name = "ACFIPLAC")
    private String acfiplac;

    /** Descripcion del activo, para que el mensaje de rechazo sea reconocible. */
    @Column(name = "ACFIOBSE")
    private String acfiobse;

    @Column(name = "ACFICEIN")
    private String acficein;

    /**
     * Bodega donde el inventario cree que esta hoy el activo. Es la que acaba en
     * {@code MOVIINVE.MOINBOFU} al recibir, asi que decide si el ERP acepta el
     * traspaso.
     */
    @Column(name = "ACFIBODE")
    private String acfibode;

    /**
     * Tipo de esa bodega ({@code BODEGA.BODETIBO}). El traspaso del ERP solo
     * mueve activos entre bodegas personales ({@code PE}); nulo si la bodega no
     * aparece en {@code BODEGA}.
     */
    @Column(name = "BODETIBO")
    private String bodetibo;
}
