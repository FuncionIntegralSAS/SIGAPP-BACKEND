package com.finte.sigapp.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * Firma digital de una requisicion de suministro (tabla FI_REQUFIRM).
 *
 * Dos filas por requisicion, creadas vacias por el paquete la primera vez que
 * una linea pasa a entregado:
 *
 * <pre>
 *   REFIPOSI = 1   quien entrega (salida)
 *   REFIPOSI = 2   quien recibe
 * </pre>
 *
 * No hay orden entre ellas: cualquiera puede firmarse primero. Que una parte ya
 * firmo se sabe porque {@code refifefi} no es nulo, no por el estado de la
 * requisicion, que se queda en {@code en} mientras se firma.
 *
 * A diferencia de FI_MOTRFIRM en traspasos, aqui si hay FK hacia el documento:
 * la terna es la llave de REQUSUMI y Oracle la admite como referencia.
 *
 * Solo se lee por JPA. La escritura la hace pro_registrar_firma, que escribe
 * fecha e imagen a la vez para que no puedan quedar descompasadas.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@IdClass(RequfirmId.class)
@Table(name = "FI_REQUFIRM")
public class RequfirmEntity {

    /** Posicion de quien entrega. */
    public static final int POSICION_SALIDA = 1;

    /** Posicion de quien recibe. */
    public static final int POSICION_RECIBE = 2;

    /** Tipo de firma de salida, tal como lo recibe la API. */
    public static final String TIPO_SALIDA = "SA";

    /** Tipo de firma de recibe, tal como lo recibe la API. */
    public static final String TIPO_RECIBE = "RE";

    @Id
    @Column(name = "REFIEMPR")
    private String refiempr; // Empresa del documento

    @Id
    @Column(name = "REFITIDO")
    private String refitido; // Tipo de documento de la requisicion

    @Id
    @Column(name = "REFINUME")
    private BigDecimal refinume; // Numero de la requisicion

    @Id
    @Column(name = "REFIPOSI")
    private Integer refiposi; // 1 salida | 2 recibe

    @Column(name = "REFICODI")
    private String reficodi; // Persona que firma. Nulo mientras no se firma

    @Column(name = "REFIFEFI")
    private LocalDateTime refifefi; // Instante exacto de la firma

    @Lob
    @Column(name = "REFIFIPE")
    private byte[] refifipe; // Imagen PNG/JPEG de la firma

    @Column(name = "REFICRAT", insertable = false, updatable = false)
    private LocalDateTime reficrat;

    @Column(name = "REFICRBY", insertable = false, updatable = false)
    private String reficrby;

    @Column(name = "REFIUPAT", insertable = false, updatable = false)
    private LocalDateTime refiupat;

    @Column(name = "REFIUPBY", insertable = false, updatable = false)
    private String refiupby;

    /**
     * Una firma cuenta solo si tiene fecha e imagen. El paquete escribe las dos
     * a la vez, pero la comprobacion no puede ir como CHECK en la tabla porque
     * Oracle no admite columnas LOB en constraints.
     */
    public boolean estaFirmada() {
        return refifefi != null && refifipe != null && refifipe.length > 0;
    }

    /** Tipo de firma que corresponde a esta posicion, para exponerlo en la API. */
    public String tipoFirma() {
        return POSICION_SALIDA == refiposi ? TIPO_SALIDA : TIPO_RECIBE;
    }
}
