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
 * Tipo de requisicion que la aplicacion puede gestionar.
 *
 * Gestionable equivale a tener equivalencia cargada: la consulta cruza
 * TIPODOCU con EQUIVALE, de modo que el conjunto de tipos es un dato de
 * configuracion y no una lista en el codigo. Las requisiciones de traslado, que
 * son integracion con el modulo comercial y no se gestionan aqui, quedan fuera
 * solas porque nadie les carga esa equivalencia.
 *
 * Si la equivalencia no esta cargada, esta consulta devuelve vacio y la bandeja
 * tambien. Es el comportamiento correcto, no un fallo.
 *
 * CUIDADO: modelo de solo lectura sobre una consulta con JOIN. No usar los
 * metodos derivados de JpaRepository.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "TIPODOCU")
public class TipoRequisicionModel {

    @Id
    @Column(name = "TIDOTIDO")
    private String codigo;

    @Column(name = "TIDODESC")
    private String nombre;
}
