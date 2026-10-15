package com.finte.sigapp.repository;

import com.finte.sigapp.model.ActivoPersonaId;
import com.finte.sigapp.model.ActivoPersonaModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Activos de una persona, para armar el formulario de traspaso.
 *
 * Va aparte de {@link ActiFijoRepository} porque devuelve otra proyeccion:
 * ademas de las columnas del activo calcula si ya esta comprometido en un
 * traspaso abierto, y un JpaRepository esta atado a una sola entidad.
 */
@Repository
public interface ActivoPersonaRepository extends JpaRepository<ActivoPersonaModel, ActivoPersonaId> {

  /**
   * Activos activos ({@code ACFIESTA = 'ac'}) de los que responde una persona.
   *
   * ENTRAMITE marca los que ya estan dentro de un traspaso en estado 'pe' o 'ap'.
   * No se excluyen de la lista a proposito: el front decide si los desactiva o
   * los muestra con una advertencia, y asi el usuario entiende por que no puede
   * seleccionarlos en vez de verlos desaparecer sin explicacion.
   *
   * La placa sale con NVL a punto para que case con FI_MOVITRAS.MOTRPLAC, que es
   * NOT NULL con esa misma convencion, y para que la clave del modelo nunca
   * llegue nula.
   *
   * La bodega es obligatoria y va contra ACFIBODE: el selector solo muestra lo
   * que la persona tiene en la bodega elegida como fuente del traspaso.
   *
   * El filtro de empresa es opcional y va contra ACFIBOEM, la empresa de la
   * bodega donde esta el activo.
   */
  @Query(value = """
      SELECT A.ACFIARTI,
             NVL(A.ACFIPLAC,'.') AS ACFIPLAC,
             A.ACFIOBSE,
             A.ACFICEIN,
             A.ACFITERC,
             CASE WHEN EXISTS (SELECT 1
                                 FROM FI_MOVITRAS T
                                WHERE T.MOTRARTI = A.ACFIARTI
                                  AND T.MOTRPLAC = NVL(A.ACFIPLAC,'.')
                                  AND T.MOTRESTA IN ('pe','ap'))
                  THEN 1
                  ELSE 0
             END AS ENTRAMITE
        FROM SRF_ACTIFIJO A
       WHERE A.ACFIPERS = :persona
         AND A.ACFIESTA = 'ac'
         AND A.ACFIBODE = :bodega
         AND (:empresa IS NULL OR A.ACFIBOEM = :empresa)
       ORDER BY A.ACFIOBSE, A.ACFIARTI
      """, nativeQuery = true)
  List<ActivoPersonaModel> buscarPorResponsable(@Param("persona") String persona,
      @Param("bodega") String bodega,
      @Param("empresa") String empresa);
}
