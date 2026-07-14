package com.finte.sigapp.repository;

import com.finte.sigapp.entity.FicofiarasEntity;
import com.finte.sigapp.entity.FicofiarasEntity2;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface FicofiarasRepository extends JpaRepository<FicofiarasEntity, Long> {

  @Query(value = """
      SELECT ARASIDAS, ARASIDCO, ARASIDBO, ARASIDAR,
             ARASIDUS, ARASNUCO, ARASCOQR, ARASCANT,
             ARASFECO, ARASEMPR, ARASPLAC, ARASESTA,
             ARASSINC, ARASFESI, ARASCNT2, ARASCNT3,
             ARASFEC2, ARASFEC3, ARASSIN2, ARASSIN3,
             ARASFES2, ARASFES3
        FROM FI_COFIARAS
       WHERE ARASESTA = 'pe'
       """, nativeQuery = true)
  List<FicofiarasEntity2> findPendientes();

  @Query(value = """
      SELECT ARASIDAS, ARASIDCO, ARASIDBO, ARASIDAR,
             ARASIDUS, ARASNUCO, ARASCOQR, ARASCANT,
             ARASFECO, ARASEMPR, ARASPLAC, ARASESTA,
             ARASSINC, ARASFESI, ARASCNT2, ARASCNT3,
             ARASFEC2, ARASFEC3, ARASSIN2, ARASSIN3,
             ARASFES2, ARASFES3, AR.ARTIDESC
       FROM FI_COFIARAS FI
       JOIN ARTICULO AR ON AR.ARTICODI = FI.ARASIDAR
      WHERE ARASESTA = 'pe'
        AND ARASIDUS = :idUsuario
         """, nativeQuery = true)
  List<FicofiarasEntity2> findPendientesPorUsuario(@Param("idUsuario") Long idUsuario);

  /** Generates the next ID for FI_COFIARAS. */
  @Query(value = "SELECT SEQ_COFIARAS.NEXTVAL FROM DUAL", nativeQuery = true)
  Long obtenerSiguienteId();

  /** Generates the next ID for FI_COFIASIG. */
  @Query(value = "SELECT SEQ_COFIASIG.NEXTVAL FROM DUAL", nativeQuery = true)
  Long obtenerIdConteo();

  @Query(value = """
      SELECT ARASIDAS, ARASIDCO, ARASIDBO, ARASIDAR,
             ARASIDUS, ARASNUCO, ARASCOQR, ARASCANT,
             ARASFECO, ARASEMPR, ARASPLAC, ARASESTA,
             ARASSINC, ARASFESI, ARASCNT2, ARASCNT3,
             ARASFEC2, ARASFEC3, ARASSIN2, ARASSIN3,
             ARASFES2, ARASFES3
        FROM FI_COFIARAS
       WHERE ARASIDUS = :userId
         AND ARASIDBO = :bodega
         AND ARASESTA = 'pe'
      """, nativeQuery = true)
  List<FicofiarasEntity> findByUserBodega(@Param("userId") Long userId,
      @Param("bodega") String bodega);

}
