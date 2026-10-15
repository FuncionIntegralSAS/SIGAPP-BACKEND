package com.finte.sigapp.repository;

import com.finte.sigapp.entity.BodegaEntity;
import com.finte.sigapp.entity.BodegaPK;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface BodegaJpaRepository extends JpaRepository<BodegaEntity, BodegaPK> {

  @Query(value = """
      SELECT T.BODECODI,T.BODEEMPR, T.BODEDESC, T.BODEESTA
       FROM SRF_BODEGA T
      WHERE T.BODEEMPR = :empresa
        AND T.BODETIBO = :tipo
      """, nativeQuery = true)
  List<BodegaEntity> findByBodeEmprAndType(@Param("empresa") String empresa, @Param("tipo") String tipo);

  @Query(value = """
      SELECT T.BODECODI, T.BODEEMPR, T.BODEDESC, T.BODEESTA
        FROM SRF_BODEGA T
        JOIN SRF_CONTARBO C
          ON T.BODEEMPR = C.COABEMPR
         AND T.BODECODI = C.COABBODE
       WHERE T.BODEEMPR = :empresa
         AND T.BODETIBO = 'FI'
         AND C.COABESTA = 'ac'
      GROUP BY T.BODECODI, T.BODEEMPR, T.BODEDESC, T.BODEESTA
      """, nativeQuery = true)
  List<BodegaEntity> findBodegasConteoActivo(@Param("empresa") String empresa);

  /**
   * Tipo de una bodega (BODETIBO), o null si no existe.
   *
   * La empresa es opcional porque el selector de activos de traspasos tambien la
   * trata asi. MAX solo sirve para devolver un escalar y null cuando no hay
   * filas; comprobado el 2026-09-24, ningun codigo de bodega tiene tipos
   * distintos entre empresas.
   */
  @Query(value = """
      SELECT MAX(T.BODETIBO)
        FROM SRF_BODEGA T
       WHERE T.BODECODI = :bodega
         AND (:empresa IS NULL OR T.BODEEMPR = :empresa)
      """, nativeQuery = true)
  String buscarTipo(@Param("bodega") String bodega, @Param("empresa") String empresa);
}
