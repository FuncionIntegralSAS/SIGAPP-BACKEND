package com.finte.sigapp.repository;

import com.finte.sigapp.model.MovitrasTraspasoModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface TransferJpaRepository extends JpaRepository<MovitrasTraspasoModel, Long> {

  /**
   * Bandeja de traspasos por estado, con filtros opcionales de empresa y bodega.
   *
   * Las descripciones de elemento, persona y bodega se resuelven con subconsultas
   * escalares (MAX garantiza una sola fila) para no multiplicar filas del tramite
   * ni depender de joins externos.
   *
   * A diferencia de FI_SIGATRAS, FI_MOVITRAS si permite filtrar por bodega: se
   * resuelve contra la bodega del activo (ACTIFIJO.ACFIBODE) mediante EXISTS.
   * Ambos filtros aceptan null, en cuyo caso no se aplican.
   */
  @Query(value = """
      SELECT T.MOTRIDMT,
             T.MOTRDREM,
             T.MOTRDRTD,
             T.MOTRDRND,
             T.MOTRPEFU,
             T.MOTRPEDE,
             T.MOTRARTI,
             T.MOTRPLAC,
             T.MOTRESTA,
             T.MOTROBSE,
             T.MOTRMORE,
             T.MOTRFECR,
             T.MOTRUSCR,
             T.MOTRFEAP,
             T.MOTRUSAP,
             T.MOTRFAFU,
             T.MOTRFADE,
             T.MOTRFERE,
             (SELECT MAX(A.ACFIOBSE)
                FROM ACTIFIJO A
               WHERE A.ACFIARTI = T.MOTRARTI
                 AND A.ACFIPLAC = T.MOTRPLAC) AS NOMBELEM,
             (SELECT MAX(P.PERSCODI || ' - ' || P.PERSNOMB || ' ' || P.PERSAPEL)
                FROM PERSONAL P
               WHERE P.PERSCODI = T.MOTRPEFU) AS PERSFUEN,
             (SELECT MAX(B.BODECODI || ' - ' || B.BODEDESC)
                FROM ACTIFIJO A
                JOIN BODEGA B ON B.BODECODI = A.ACFIBODE
                             AND B.BODEEMPR = T.MOTRDREM
               WHERE A.ACFIARTI = T.MOTRARTI
                 AND A.ACFIPLAC = T.MOTRPLAC) AS BODEFUEN,
             (SELECT MAX(P.PERSCODI || ' - ' || P.PERSNOMB || ' ' || P.PERSAPEL)
                FROM PERSONAL P
               WHERE P.PERSCODI = T.MOTRPEDE) AS PERSDEST,
             (SELECT MAX(B.BODECODI || ' - ' || B.BODEDESC)
                FROM PERSONAL P
                JOIN BODEGA B ON B.BODEDIVI = P.PERSDIVI
                             AND B.BODETIBO = 'PE'
               WHERE P.PERSCODI = T.MOTRPEDE) AS BODEDEST
        FROM FI_MOVITRAS T
       WHERE T.MOTRESTA IN (:estados)
         AND (:empresa IS NULL OR T.MOTRDREM = :empresa)
         AND (:bodega IS NULL
              OR EXISTS (SELECT 1
                           FROM ACTIFIJO A
                          WHERE A.ACFIARTI = T.MOTRARTI
                            AND A.ACFIPLAC = T.MOTRPLAC
                            AND A.ACFIBODE = :bodega))
       ORDER BY T.MOTRFECR DESC
      """, nativeQuery = true)
  List<MovitrasTraspasoModel> buscarPorEstados(@Param("estados") List<String> estados,
      @Param("empresa") String empresa,
      @Param("bodega") String bodega);
}
