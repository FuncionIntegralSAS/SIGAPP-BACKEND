package com.finte.sigapp.repository;

import com.finte.sigapp.model.RequisicionCabeceraModel;
import com.finte.sigapp.model.RequisicionId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.Optional;

/**
 * Cabecera de una requisicion para el detalle (REQUSUMI). Solo lectura.
 */
@Repository
public interface RequisicionCabeceraRepository extends JpaRepository<RequisicionCabeceraModel, RequisicionId> {

    @Query(value = """
            SELECT R.RESUTDEM, R.RESUTIDO, R.RESUNUME, R.RESUFECH, R.RESUFERE,
                   R.RESUOBSE, R.RESUDIVI, R.RESUCIUD, R.RESUBODE, R.RESUBODS,
                   R.RESUCEIN, R.RESUTERC
              FROM REQUSUMI R
             WHERE R.RESUTDEM = :empresa
               AND R.RESUTIDO = :tipoDocumento
               AND R.RESUNUME = :numero
            """, nativeQuery = true)
    Optional<RequisicionCabeceraModel> buscarCabecera(@Param("empresa") String empresa,
                                                      @Param("tipoDocumento") String tipoDocumento,
                                                      @Param("numero") BigDecimal numero);
}
