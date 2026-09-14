package com.finte.sigapp.service;

import com.finte.sigapp.dto.request.RequisicionAnularRequest;
import com.finte.sigapp.dto.request.RequisicionFirmaRequest;
import com.finte.sigapp.dto.request.RequisicionLineasRequest;
import com.finte.sigapp.dto.request.RequisicionRegistrarRequest;
import com.finte.sigapp.dto.response.ObjectListResponse;
import com.finte.sigapp.dto.response.ObjectResponse;

import java.time.LocalDate;

/**
 * Ciclo de vida de las requisiciones de suministro desde la aplicacion movil.
 *
 * La requisicion NO se crea aqui: nace en el ERP en estado inicial y este
 * modulo la hace avanzar.
 *
 * <pre>
 *   in -> aprobar -> ap -> entregar -> en -> [dos firmas] -> registrar -> rg
 *                                       \                                 + DOCUINVE
 *                                        -> anular -> ae                  + N MOVIINVE
 * </pre>
 *
 * Se identifica por la terna (empresa, tipo de documento, numero), que es la
 * llave que ya tiene en el ERP. No hay identificador propio.
 *
 * Convencion de respuesta del proyecto: {@code code = 0} exito, {@code code =
 * -1} rechazo de negocio. El service devuelve el sobre ya armado y el
 * controller solo captura lo tecnico.
 */
public interface RequisicionService {

    /**
     * Tipos de requisicion gestionables. Si devuelve vacio es que no hay
     * equivalencias cargadas, y entonces tampoco habra bandeja.
     */
    ObjectListResponse listarTipos();

    /** Bandeja filtrada por estado derivado, con el resto de filtros opcionales. */
    ObjectListResponse listarRequisiciones(String estado, String empresa, String tipoDocumento,
                                           String bodega, LocalDate desde);

    /** Cabecera, lineas con sus cantidades y placas, y las dos firmas. */
    ObjectResponse obtenerDetalle(String empresa, String tipoDocumento, Long numero);

    /** Aprueba las lineas indicadas con su cantidad. Las que no vengan no se tocan. */
    ObjectResponse aprobar(String empresa, String tipoDocumento, Long numero,
                           RequisicionLineasRequest request, String bearerToken);

    /**
     * Entrega las lineas indicadas.
     *
     * Las placas de los articulos devolutivos son opcionales: si no vienen, el
     * paquete las asigna por orden de entrada del activo (FIFO). Enviarlas
     * sobreescribe esa eleccion para la linea.
     */
    ObjectResponse entregar(String empresa, String tipoDocumento, Long numero,
                            RequisicionLineasRequest request, String bearerToken);

    /** Anula al entregar las lineas indicadas. Solo aplica a lineas sin entrega. */
    ObjectResponse anular(String empresa, String tipoDocumento, Long numero,
                          RequisicionAnularRequest request, String bearerToken);

    /** Registra una de las dos firmas. Sin orden entre ellas. */
    ObjectResponse registrarFirma(String empresa, String tipoDocumento, Long numero,
                                  RequisicionFirmaRequest request, String bearerToken);

    /** Los bytes de una firma, para servirla como imagen. */
    byte[] obtenerFirma(String empresa, String tipoDocumento, Long numero, String tipoFirma);

    /**
     * Cierra el ciclo: exige las dos firmas y genera la salida de inventario.
     *
     * Punto de no retorno: crea el documento en el ERP y eso no se deshace desde
     * la aplicacion.
     */
    ObjectResponse registrarSalida(String empresa, String tipoDocumento, Long numero,
                                   RequisicionRegistrarRequest request, String bearerToken);
}
