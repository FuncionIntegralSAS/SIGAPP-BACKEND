package com.finte.sigapp.repository;

import com.finte.sigapp.model.ActivoCompatibilidadModel;
import com.finte.sigapp.model.ActivoPersonaId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;

/**
 * Centro de informacion de un conjunto de activos, para comprobar antes de crear
 * un traspaso que todos pueden ir en el mismo documento.
 */
@Repository
public interface ActivoCompatibilidadRepository extends JpaRepository<ActivoCompatibilidadModel, ActivoPersonaId> {

    /**
     * Datos de cabecera de los activos indicados.
     *
     * Filtra por CODIGO de articulo y no por el par articulo-placa: una lista de
     * pares no se expresa comodamente en un IN, y el cruce exacto se hace
     * despues en Java. Es el mismo criterio que usa la busqueda de activos ya
     * comprometidos, para que las dos validaciones se lean igual.
     *
     * La placa sale con NVL a punto para que la clave case con la que usa el
     * resto del modulo, donde el elemento sin placa se guarda como '.'.
     *
     * La bodega va con LEFT JOIN: un activo con una bodega que no esta en BODEGA
     * sale con tipo nulo en vez de desaparecer, y asi el rechazo lo nombra.
     */
    @Query(value = """
            SELECT A.ACFIARTI,
                   NVL(A.ACFIPLAC,'.') AS ACFIPLAC,
                   A.ACFIOBSE,
                   A.ACFICEIN,
                   A.ACFIBODE,
                   B.BODETIBO
              FROM SRF_ACTIFIJO A
              LEFT JOIN SRF_BODEGA B
                ON B.BODECODI = A.ACFIBODE
               AND B.BODEEMPR = A.ACFIBOEM
             WHERE A.ACFIARTI IN (:articulos)
               AND A.ACFIDIEM = :empresa
            """, nativeQuery = true)
    List<ActivoCompatibilidadModel> buscarDatosDeCabecera(@Param("articulos") Collection<String> articulos,
                                                          @Param("empresa") String empresa);
}
