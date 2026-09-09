package com.finte.sigapp.repository;

import com.finte.sigapp.entity.MotrfirmEntity;
import com.finte.sigapp.entity.MotrfirmId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Lectura de las firmas de un traspaso (FI_MOTRFIRM).
 *
 * Solo lectura: la escritura la hace pro_registrar_firma a traves de
 * {@link TransferRepository}, porque tiene que validar el estado del tramite en
 * la misma transaccion.
 */
@Repository
public interface MotrfirmRepository extends JpaRepository<MotrfirmEntity, MotrfirmId> {

    /** Las dos firmas del tramite, entrega primero. */
    List<MotrfirmEntity> findByMofinutrOrderByMofiposi(Long mofinutr);

    /**
     * Una firma concreta. Se usa para servirla como imagen, donde traer las dos
     * seria cargar un BLOB de mas.
     */
    Optional<MotrfirmEntity> findByMofinutrAndMofiposi(Long mofinutr, Integer mofiposi);
}
