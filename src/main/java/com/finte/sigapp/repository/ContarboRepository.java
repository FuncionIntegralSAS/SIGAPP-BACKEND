package com.finte.sigapp.repository;

import com.finte.sigapp.entity.ContarboEntity;
import com.finte.sigapp.entity.ContarboPK;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface ContarboRepository extends JpaRepository<ContarboEntity, ContarboPK> {
  @Query(value = """
      SELECT
      C.COABEMPR,C.COABBODE,C.COABBOLO,
      C.COABFECH,C.COABARTI,C.COABPLAC,
      C.COABCAC1,C.COABCAC2,C.COABCAC3,
      C.COABCABO,C.COABDIEM,C.COABDITD,
      C.COABDIND,C.COABFESI,C.COABDIGI,
      C.COABTERM,C.COABESTA,C.COABVAUN,C.COABFECI
      FROM CONTARBO C
      WHERE C.COABEMPR = :empresa
        AND C.COABBODE = :bodega
        AND C.COABFECH BETWEEN
            TO_DATE(:fecha ||' 00:00:00','DD/MM/YYYY HH24:Mi:SS')
        AND TO_DATE(:fecha ||' 23:59:59','DD/MM/YYYY HH24:Mi:SS')
        AND C.COABESTA = 'ce'
        ORDER BY C.COABARTI ASC
      """, nativeQuery = true)
  List<ContarboEntity> obtenerArticulosOrdenados(@Param("empresa") String empresa,
      @Param("bodega") String bodega,
      @Param("fecha") String fecha);

  @Query(value = """
      SELECT
          C.COABEMPR, C.COABBODE, C.COABBOLO,
          C.COABFECH, C.COABARTI, C.COABPLAC,
          C.COABCAC1, C.COABCAC2, C.COABCAC3,
          C.COABCABO, C.COABDIEM, C.COABDITD,
          C.COABDIND, C.COABFESI, C.COABDIGI,
          C.COABTERM, C.COABESTA, C.COABVAUN, C.COABFECI
      FROM CONTARBO C
      WHERE C.COABBODE  = :bodega
        AND C.COABARTI IN (:idArticulos)
      ORDER BY C.COABARTI ASC
      """, nativeQuery = true)
  List<ContarboEntity> findByBodegaYArticulos(
      @Param("bodega") String bodega,
      @Param("idArticulos") List<Long> idArticulos);

}