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

import java.time.LocalDateTime;

/**
 * Firma de aceptacion de un traspaso (tabla FI_MOTRFIRM).
 *
 * Dos filas por tramite, creadas vacias por el paquete cuando se abre el
 * traspaso:
 *
 * <pre>
 *   MOFIPOSI = 1   la persona que entrega (fuente)
 *   MOFIPOSI = 2   la persona que recibe  (destino)
 * </pre>
 *
 * No hay orden entre ellas: cualquiera puede firmarse primero. Que una parte ya
 * firmo se sabe porque {@code mofifefi} no es nulo, no por el estado del
 * tramite, que se queda en {@code ap} mientras se firma.
 *
 * La clave es compuesta ({@code mofinutr}, {@code mofiposi}) y no hay FK hacia
 * FI_MOVITRAS: MOTRNUTR se repite en las N lineas del tramite y Oracle exige
 * unicidad en la columna referenciada. La integridad la sostiene el paquete.
 *
 * Solo se lee por JPA. La escritura la hace pro_registrar_firma, que escribe
 * fecha e imagen a la vez.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@IdClass(MotrfirmId.class)
@Table(name = "FI_MOTRFIRM")
public class MotrfirmEntity {

    /** Posicion de la persona que entrega. */
    public static final int POSICION_FUENTE = 1;

    /** Posicion de la persona que recibe. */
    public static final int POSICION_DESTINO = 2;

    @Id
    @Column(name = "MOFINUTR")
    private Long mofinutr; // Numero de tramite

    @Id
    @Column(name = "MOFIPOSI")
    private Integer mofiposi; // 1 entrega | 2 recibe

    @Column(name = "MOFICODI")
    private String moficodi; // Persona que firma

    @Column(name = "MOFIFEFI")
    private LocalDateTime mofifefi; // Instante de la firma. Null = sin firmar

    /**
     * Imagen PNG o JPEG. Bytes, no base64: el base64 es formato de transporte
     * hacia el front, no de almacenamiento.
     */
    @Lob
    @Column(name = "MOFIFIPE")
    private byte[] mofifipe;

    /* Auditoria tecnica, la escribe la BD */

    @Column(name = "MOFICRAT", insertable = false, updatable = false)
    private LocalDateTime moficrat;

    @Column(name = "MOFICRBY", insertable = false, updatable = false)
    private String moficrby;

    @Column(name = "MOFIUPAT", insertable = false, updatable = false)
    private LocalDateTime mofiupat;

    @Column(name = "MOFIUPBY", insertable = false, updatable = false)
    private String mofiupby;

    /** Una firma esta registrada cuando tiene fecha e imagen. */
    public boolean estaFirmada() {
        return mofifefi != null && mofifipe != null && mofifipe.length > 0;
    }
}
