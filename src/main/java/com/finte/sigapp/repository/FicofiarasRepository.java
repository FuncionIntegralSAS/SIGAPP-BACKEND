package com.finte.sigapp.repository;

import com.finte.sigapp.entity.FicofiarasEntity;

import jakarta.persistence.Column;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Repository for FI_COFIARAS table.
 * Provides queries for pending articles using the new 8‑character column names.
 */
@Repository
public interface FicofiarasRepository extends JpaRepository<FicofiarasEntity, Long> {

    /**
     * Retrieves all pending articles (status 'PE').
     */
    @Query(value = "SELECT ARASIDAS, ARASIDCO, ARASIDBO, ARASIDAR, ARASIDUS, ARASNUCO, ARASCOQR, ARASCANT, ARASFECO, ARASEMPR, ARASPLAC, ARASESTA, ARASSINC, ARASFESI, ARASCNT2, ARASCNT3, ARASFEC2, ARASFEC3, ARASSIN2, ARASSIN3, ARASFES2, ARASFES3 FROM FI_COFIARAS WHERE ARASESTA = 'PE'", nativeQuery = true)
    List<FicofiarasEntity> findPendientes();

    /**
     * Retrieves pending articles (status 'PE') for a specific user.
     */
    @Query(value = """
            SELECT ARASIDAS, ARASIDCO, ARASIDBO, ARASIDAR,
                   ARASIDUS, ARASNUCO, ARASCOQR, ARASCANT,
                   ARASFECO, ARASEMPR, ARASPLAC, ARASESTA,
                   ARASSINC, ARASFESI, ARASCNT2, ARASCNT3,
                   ARASFEC2, ARASFEC3, ARASSIN2, ARASSIN3,
                   ARASFES2, ARASFES3
             FROM FI_COFIARAS
            WHERE ARASESTA = 'pe'
              AND ARASIDUS = :idUsuario""", nativeQuery = true)
    List<FicofiarasEntity> findPendientesPorUsuario(@Param("idUsuario") Long idUsuario);

    /** Generates the next ID for FI_COFIARAS. */
    @Query(value = "SELECT SEQ_COFIARAS.NEXTVAL FROM DUAL", nativeQuery = true)
    Long obtenerSiguienteId();

    /** Generates the next ID for FI_COFIASIG. */
    @Query(value = "SELECT SEQ_COFIASIG.NEXTVAL FROM DUAL", nativeQuery = true)
    Long obtenerIdConteo();

}
