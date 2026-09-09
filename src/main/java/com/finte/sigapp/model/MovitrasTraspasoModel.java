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
 * Vista de bandeja de los traspasos: UNA FILA POR TRAMITE.
 *
 * FI_MOVITRAS guarda una fila por articulo, asi que la consulta de
 * {@link com.finte.sigapp.repository.TransferJpaRepository} agrupa por MOTRNUTR.
 * Por eso la clave de este modelo es el numero de tramite y no MOTRIDMT, que
 * aqui ni siquiera aparece.
 *
 * Solo lo necesario para listar y enlazar. El resto lo sirve el detalle, que usa
 * {@link com.finte.sigapp.entity.MovitrasEntity} y
 * {@link com.finte.sigapp.entity.MotrfirmEntity}.
 *
 * Es de solo lectura y solo debe consultarse con la query nativa: sus columnas
 * son el resultado de una agrupacion, y los metodos derivados de JpaRepository
 * generarian SQL sin ella.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "FI_MOVITRAS")
public class MovitrasTraspasoModel {

    @Id
    @Column(name = "MOTRNUTR")
    private Long motrnutr; // Numero de tramite

    @Column(name = "MOTRDRTD")
    private String motrdrtd; // Tipo de documento

    @Column(name = "MOTRDRND")
    private BigDecimal motrdrnd; // Numero del documento (REQUSUMI.RESUNUME)

    @Column(name = "MOTRFECR")
    private LocalDateTime motrfecr; // Fecha de creacion del tramite
}
