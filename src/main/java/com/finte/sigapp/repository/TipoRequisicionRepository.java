package com.finte.sigapp.repository;

import com.finte.sigapp.model.TipoRequisicionModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Tipos de requisicion que la aplicacion puede gestionar.
 */
@Repository
public interface TipoRequisicionRepository extends JpaRepository<TipoRequisicionModel, String> {

    /**
     * Los tipos con equivalencia activa, que es la definicion de gestionable.
     *
     * OJO SI DEVUELVE VACIO: significa que no hay ninguna fila COBO/REQU en
     * EQUIVALE, y entonces la bandeja tambien saldra vacia y no se podra
     * registrar nada. Hay dos tablas EQUIVALE en la base -una en SRFPNACUA y
     * otra en FPLPNACUA- y esta consulta ve la del esquema de la conexion, que
     * es la misma que ve el paquete. Antes de buscar el fallo en otro sitio,
     * comprobar donde quedo cargada la equivalencia.
     *
     * No se expone el tipo de salida (EQUICOEQ): es un detalle de la generacion
     * del documento, el front no lo usa y devolverlo invitaria a que alguien lo
     * mandara de vuelta.
     */
    @Query(value = """
            SELECT T.TIDOTIDO, T.TIDODESC
              FROM TIPODOCU T
              JOIN EQUIVALE E ON E.EQUICODI = T.TIDOTIDO
                             AND E.EQUICLEQ = 'COBO'
                             AND E.EQUITIPO = 'REQU'
                             AND E.EQUIESTA = 'ac'
             ORDER BY T.TIDODESC
            """, nativeQuery = true)
    List<TipoRequisicionModel> buscarGestionables();
}
