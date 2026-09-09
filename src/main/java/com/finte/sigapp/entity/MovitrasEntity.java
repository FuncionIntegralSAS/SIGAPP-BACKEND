package com.finte.sigapp.entity;

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
 * Linea de un traspaso (tabla FI_MOVITRAS): UN ARTICULO.
 *
 * Un tramite mueve N articulos y por tanto tiene N filas. Lo que las agrupa es
 * {@code motrnutr}, el numero de tramite. {@code motridmt} es solo la clave
 * tecnica de la fila: no se usa como identificador de negocio y no aparece en
 * ningun endpoint.
 *
 * De {@code motrdrem} a {@code motrfere} el contenido se repite en las N lineas,
 * asi que se leen indistintamente de cualquiera de ellas. Escribirlas es otra
 * cosa: la escritura no pasa por JPA, la ejecuta el paquete PL/SQL
 * PKG_FI_MOVITRAS filtrando siempre por MOTRNUTR.
 *
 * Las firmas no estan aqui: viven en {@link MotrfirmEntity}, dos filas por
 * tramite en vez de repetidas en cada linea.
 *
 * La bandeja no usa esta entidad sino
 * {@link com.finte.sigapp.model.MovitrasTraspasoModel}, que agrupa por tramite y
 * agrega las descripciones resueltas por subconsulta.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "FI_MOVITRAS")
public class MovitrasEntity {

    @Id
    @Column(name = "MOTRIDMT")
    private Long motridmt; // Clave tecnica de la linea

    @Column(name = "MOTRNUTR")
    private Long motrnutr; // Numero de tramite: agrupa las lineas del traspaso

    /*
     * Documento de requisicion al que pertenece el traspaso. Las tres columnas
     * forman la FK hacia REQUSUMI (RESUTDEM, RESUTIDO, RESUNUME).
     */

    @Column(name = "MOTRDREM")
    private String motrdrem; // Empresa del documento

    @Column(name = "MOTRDRTD")
    private String motrdrtd; // Tipo de documento

    @Column(name = "MOTRDRND")
    private BigDecimal motrdrnd; // Numero del documento

    @Column(name = "MOTRPEFU")
    private String motrpefu; // Persona fuente (responsable actual)

    @Column(name = "MOTRPEDE")
    private String motrpede; // Persona destino (quien recibe)

    /* Lo unico propio de la linea */

    @Column(name = "MOTRARTI")
    private String motrarti; // Elemento / articulo

    @Column(name = "MOTRPLAC")
    private String motrplac; // Placa del elemento. Punto cuando no tiene

    @Column(name = "MOTRESTA")
    private String motresta; // pe | ap | na | re

    @Column(name = "MOTROBSE")
    private String motrobse; // Observacion / motivo del traspaso

    @Column(name = "MOTRMORE")
    private String motrmore; // Motivo de rechazo

    /* Trazabilidad del tramite */

    @Column(name = "MOTRFECR")
    private LocalDateTime motrfecr; // Fecha de creacion

    @Column(name = "MOTRUSCR")
    private String motruscr; // Usuario que crea

    @Column(name = "MOTRFEAP")
    private LocalDateTime motrfeap; // Fecha de aprobacion

    @Column(name = "MOTRUSAP")
    private String motrusap; // Usuario que aprueba

    @Column(name = "MOTRFERE")
    private LocalDateTime motrfere; // Fecha en que el destino recibe

    /* Auditoria tecnica, la escribe la BD */

    @Column(name = "MOTRCRAT", insertable = false, updatable = false)
    private LocalDateTime motrcrat;

    @Column(name = "MOTRCRBY", insertable = false, updatable = false)
    private String motrcrby;

    @Column(name = "MOTRUPAT", insertable = false, updatable = false)
    private LocalDateTime motrupat;

    @Column(name = "MOTRUPBY", insertable = false, updatable = false)
    private String motrupby;
}
