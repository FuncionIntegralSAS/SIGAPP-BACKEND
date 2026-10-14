package com.finte.sigapp.repository;

import com.finte.sigapp.model.ParametroModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * Lectura directa de un parametro general del ERP (PARAOPER).
 */
@Repository
public interface ParametroRepository extends JpaRepository<ParametroModel, String> {

    /**
     * Lee la tabla y no pasa por FunBuscPara a proposito. La funcion del ERP
     * resuelve el valor segun el usuario y la empresa de la SESION Oracle
     * (pkgfnPersonal.getUsuario, pkgFnEmpresa.getEmpresa), que son variables de
     * paquete: en un endpoint publico no hay usuario que establecer, y con un
     * pool de conexiones quedaria el que haya dejado la peticion anterior.
     *
     * Se consulta SRF_PARAOPER y no PARAOPER a secas: para el usuario de
     * interfaz (ARQGUI) ese nombre apunta a la PARAOPER del FPL, que es otra
     * tabla, con otras columnas y sin este parametro. El script de carga va a la
     * del SRF.
     */
    @Query(value = """
            SELECT P.PARACODI, P.PARACOVA
              FROM SRF_PARAOPER P
             WHERE P.PARACODI = :codigo
            """, nativeQuery = true)
    Optional<ParametroModel> buscarPorCodigo(@Param("codigo") String codigo);
}
