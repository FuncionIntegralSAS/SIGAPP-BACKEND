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
 * Una fila de la bandeja de requisiciones: un documento, no una linea.
 *
 * MOVIRESU tiene una fila por articulo, asi que la consulta agrupa por la terna
 * y calcula el estado del conjunto. Las columnas ESTADO y LINEAS no existen en
 * la tabla: son derivadas del GROUP BY.
 *
 * CUIDADO: por eso mismo este modelo solo puede consultarse con su query
 * nativa. {@code findAll}, {@code findById} o {@code save} generarian SQL con
 * columnas inexistentes y fallarian con ORA-00904.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@IdClass(RequisicionId.class)
@Table(name = "MOVIRESU")
public class RequisicionBandejaModel {

    @Id
    @Column(name = "EMPRESA")
    private String empresa;

    @Id
    @Column(name = "TIPODOCU")
    private String tipoDocumento;

    @Id
    @Column(name = "NUMERO")
    private BigDecimal numero;

    /** Estado derivado de las lineas, con la precedencia de fun_estado. */
    @Column(name = "ESTADO")
    private String estado;

    @Column(name = "FECHA")
    private LocalDate fecha;

    @Column(name = "BODEGA")
    private String bodega;

    /** Cuantas lineas tiene la requisicion. Da el tamano del trabajo pendiente. */
    @Column(name = "LINEAS")
    private Integer lineas;
}
