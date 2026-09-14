package com.finte.sigapp.repository;

import com.finte.sigapp.entity.MoviresuEntity;
import com.finte.sigapp.entity.MoviresuId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

/**
 * Lectura de las lineas de una requisicion (MOVIRESU).
 *
 * SOLO LECTURA. La escritura la hace PKG_FI_REQUISICION a traves de
 * {@link RequisicionRepository}: cada UPDATE dispara un trigger del ERP que
 * valida y propaga, y el paquete ademas bloquea la requisicion entera antes de
 * tocar nada. Un save() desde aqui saltaria ese bloqueo.
 */
@Repository
public interface MoviresuRepository extends JpaRepository<MoviresuEntity, MoviresuId> {

    /**
     * Las lineas de una requisicion, en el mismo orden en que el paquete las
     * bloquea.
     */
    List<MoviresuEntity> findByMorstdemAndMorstidoAndMorsrenuOrderByMorsartiAscMorsbodeAscMorssecuAsc(
            String morstdem, String morstido, BigDecimal morsrenu);
}
