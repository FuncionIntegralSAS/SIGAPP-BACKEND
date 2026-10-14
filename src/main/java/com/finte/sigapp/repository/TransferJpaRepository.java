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
   * Devuelve UNA FILA POR TRAMITE, no por articulo: FI_MOVITRAS tiene N lineas y
   * aqui se agrupan por MOTRNUTR. El tipo, el numero de documento y la fecha se
   * toman con MIN porque son identicos en las N filas del tramite.
   *
   * Es deliberadamente minima: la bandeja lista y enlaza, y el detalle lo sirve
   * {@link MovitrasRepository#findByMotrnutrOrderByMotridmt}. Por eso no resuelve
   * nombres de personas, ni cuenta articulos, ni consulta las firmas. No queda
   * ninguna subconsulta en la proyeccion.
   *
   * El unico acceso a otra tabla es el filtro de bodega, que usa EXISTS sobre el
   * tramite completo: si CUALQUIERA de sus articulos esta en esa bodega, el
   * tramite aparece. Es un filtro, no una proyeccion.
   */
  @Query(value = """
      SELECT T.MOTRNUTR,
             MIN(T.MOTRDRTD) AS MOTRDRTD,
             MIN(T.MOTRDRND) AS MOTRDRND,
             MIN(T.MOTRFECR) AS MOTRFECR
        FROM FI_MOVITRAS T
       WHERE T.MOTRESTA IN (:estados)
         AND (:empresa IS NULL OR T.MOTRDREM = :empresa)
         AND (:bodega IS NULL
              OR EXISTS (SELECT 1
                           FROM FI_MOVITRAS T2
                           JOIN SRF_ACTIFIJO A2
                             ON A2.ACFIARTI = T2.MOTRARTI
                            AND NVL(A2.ACFIPLAC,'.') = T2.MOTRPLAC
                          WHERE T2.MOTRNUTR = T.MOTRNUTR
                            AND A2.ACFIBODE = :bodega))
       GROUP BY T.MOTRNUTR
       ORDER BY MIN(T.MOTRFECR) DESC
      """, nativeQuery = true)
  List<MovitrasTraspasoModel> buscarPorEstados(@Param("estados") List<String> estados,
      @Param("empresa") String empresa,
      @Param("bodega") String bodega);
}
