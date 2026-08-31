package com.finte.sigapp.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Movimiento de traspaso (tabla FI_MOVITRAS). Reemplaza a FI_SIGATRAS.
 *
 * Mapea unicamente columnas fisicas, por lo que los metodos derivados de
 * JpaRepository (findById, findAll) funcionan sin restricciones. La bandeja, en
 * cambio, usa {@link com.finte.sigapp.model.MovitrasTraspasoModel}, que agrega
 * las descripciones resueltas por subconsulta.
 *
 * La escritura no pasa por JPA: el ciclo del tramite lo ejecuta el paquete
 * PL/SQL PKG_FI_MOVITRAS, que es donde se resuelven, contra los parametros del
 * sistema, los movimientos con los que se inserta en el ERP.
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
    private Long motridmt; // Identificador del traspaso

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

    @Column(name = "MOTRARTI")
    private String motrarti; // Elemento / articulo

    @Column(name = "MOTRPLAC")
    private String motrplac; // Placa del elemento

    @Column(name = "MOTRESTA")
    private String motresta; // pe | ap | na | af | ad | re

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

    @Column(name = "MOTRFAFU")
    private LocalDateTime motrfafu; // Fecha de aceptacion de la fuente

    @Column(name = "MOTRFADE")
    private LocalDateTime motrfade; // Fecha de aceptacion del destino

    @Column(name = "MOTRFERE")
    private LocalDateTime motrfere; // Fecha en que el destino recibe

    /*
     * Firmas. Son BLOB con los bytes de la imagen (no el base64) y pesan, por lo
     * que la bandeja no las consulta: solo se cargan en el detalle de un traspaso.
     * El base64 es formato de transporte hacia el front, no de almacenamiento.
     */

    @Lob
    @Column(name = "MOTRFIFU")
    private byte[] motrfifu; // Firma fuente (imagen PNG/JPEG)

    @Lob
    @Column(name = "MOTRFIDE")
    private byte[] motrfide; // Firma destino (imagen PNG/JPEG)

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
