package com.finte.sigapp.repository;

import com.finte.sigapp.model.RequisicionBandejaModel;
import com.finte.sigapp.model.RequisicionId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;

/**
 * Bandeja de requisiciones: una fila por documento, no por articulo.
 */
@Repository
public interface RequisicionBandejaRepository extends JpaRepository<RequisicionBandejaModel, RequisicionId> {

    /**
     * Requisiciones gestionables, agrupadas por documento y filtradas por su
     * estado derivado.
     *
     * Tres decisiones que no se ven a simple vista:
     *
     * El EXISTS contra EQUIVALE es el filtro de tipos gestionables. No hay lista
     * de tipos en el codigo: gestionable es tener equivalencia cargada, de modo
     * que las requisiciones de traslado -integracion con el modulo comercial-
     * quedan fuera solas porque nadie se la carga.
     *
     * El estado se calcula con la misma precedencia que fun_estado en el
     * paquete: manda la fase mas atrasada que quede en alguna linea. Va en una
     * subconsulta para no repetir el CASE en el HAVING.
     *
     * La ventana temporal y el tope no son cosmeticos: MOVIRESU tiene mas de
     * cien mil lineas registradas y sin cota una consulta por estado 'rg'
     * traeria media tabla.
     */
    @Query(value = """
            SELECT * FROM (
                SELECT M.MORSTDEM AS EMPRESA,
                       M.MORSTIDO AS TIPODOCU,
                       M.MORSRENU AS NUMERO,
                       CASE MIN(CASE M.MORSESTA
                                     WHEN 'in' THEN 1
                                     WHEN 'ap' THEN 2
                                     WHEN 'en' THEN 3
                                     WHEN 'rg' THEN 4
                                     ELSE 5 END)
                            WHEN 1 THEN 'in'
                            WHEN 2 THEN 'ap'
                            WHEN 3 THEN 'en'
                            WHEN 4 THEN 'rg'
                            ELSE 'ae' END AS ESTADO,
                       MIN(M.MORSFECH) AS FECHA,
                       MIN(M.MORSBODE) AS BODEGA,
                       COUNT(1)        AS LINEAS
                  FROM MOVIRESU M
                 WHERE EXISTS (SELECT 1
                                 FROM EQUIVALE E
                                WHERE E.EQUICLEQ = 'COBO'
                                  AND E.EQUITIPO = 'REQU'
                                  AND E.EQUICODI = M.MORSTIDO
                                  AND E.EQUIESTA = 'ac')
                   AND (:empresa IS NULL OR M.MORSTDEM = :empresa)
                   AND (:tipoDocumento IS NULL OR M.MORSTIDO = :tipoDocumento)
                   AND (:bodega IS NULL OR M.MORSBODE = :bodega)
                   AND M.MORSFECH >= :desde
                 GROUP BY M.MORSTDEM, M.MORSTIDO, M.MORSRENU
            )
            WHERE (:estado IS NULL OR ESTADO = :estado)
            ORDER BY FECHA DESC, NUMERO DESC
            FETCH FIRST :limite ROWS ONLY
            """, nativeQuery = true)
    List<RequisicionBandejaModel> buscarBandeja(@Param("estado") String estado,
                                                @Param("empresa") String empresa,
                                                @Param("tipoDocumento") String tipoDocumento,
                                                @Param("bodega") String bodega,
                                                @Param("desde") LocalDate desde,
                                                @Param("limite") int limite);
}
