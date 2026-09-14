package com.finte.sigapp.repository;

import com.finte.sigapp.entity.RequfirmEntity;
import com.finte.sigapp.entity.RequfirmId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;

/**
 * Lectura de las firmas de una requisicion (FI_REQUFIRM).
 *
 * Solo lectura: la escritura la hace pro_registrar_firma a traves de
 * {@link RequisicionRepository}, porque tiene que comprobar el estado de la
 * requisicion dentro del mismo bloqueo.
 */
@Repository
public interface RequfirmRepository extends JpaRepository<RequfirmEntity, RequfirmId> {

    /** Las dos firmas de la requisicion, salida primero. */
    List<RequfirmEntity> findByRefiemprAndRefitidoAndRefinumeOrderByRefiposi(
            String refiempr, String refitido, BigDecimal refinume);

    /**
     * Una firma concreta. Se usa para servirla como imagen, donde traer las dos
     * seria cargar un BLOB de mas.
     */
    Optional<RequfirmEntity> findByRefiemprAndRefitidoAndRefinumeAndRefiposi(
            String refiempr, String refitido, BigDecimal refinume, Integer refiposi);
}
