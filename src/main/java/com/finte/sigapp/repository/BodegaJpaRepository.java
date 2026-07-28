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
       FROM bodega T
      WHERE T.BODEEMPR = :EMPRESA
        AND T.BODETIBO = 'FI'
      """, nativeQuery = true)
  List<BodegaEntity> findByBodeEmpr(@Param("EMPRESA") String empresa);

  @Query(value = """
      SELECT T.BODECODI, T.BODEEMPR, T.BODEDESC, T.BODEESTA
        FROM BODEGA T
        JOIN CONTARBO C
          ON T.BODEEMPR = C.COABDIEM
         AND T.BODECODI = C.COABBODE
       WHERE T.BODEEMPR = :empresa
         AND T.BODETIBO = 'FI'
         AND C.COABESTA = 'ac'
      GROUP BY T.BODECODI, T.BODEEMPR, T.BODEDESC, T.BODEESTA
      """, nativeQuery = true)
  List<BodegaEntity> findBodegasConteoActivo(@Param("empresa") String empresa);
}
