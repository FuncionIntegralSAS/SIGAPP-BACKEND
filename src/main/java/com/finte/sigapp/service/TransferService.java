package com.finte.sigapp.service;

import com.finte.sigapp.dto.request.TransferProcessRequest;
import com.finte.sigapp.dto.request.TransferRequest;
import com.finte.sigapp.dto.request.TransferSignRequest;
import com.finte.sigapp.dto.response.ObjectListResponse;
import com.finte.sigapp.dto.response.ObjectResponse;

/**
 * Ciclo de vida de un traspaso (FI_MOVITRAS):
 *
 * <pre>
 *   crear (pe) -> aprobar (ap) -> firma fuente (af) -> firma destino (ad) -> recibir (re)
 *                     \
 *                      -> rechazar (na)
 * </pre>
 *
 * Los metodos devuelven ya armada la envoltura de respuesta: code 0 cuando el
 * proceso fue exitoso y code -1 cuando la operacion se rechaza por reglas de
 * negocio. Los errores tecnicos se propagan como excepcion para que los maneje
 * el controller.
 *
 * Las operaciones que dejan trazabilidad reciben el header Authorization
 * completo: el servicio es el responsable de resolver el usuario a partir del
 * token.
 */
public interface TransferService {

    ObjectResponse crearSolicitud(TransferRequest request, String bearerToken);

    ObjectListResponse listarSolicitudes(String empresa, String bodega, String estadoFront);

    ObjectResponse obtenerDetalle(Long idTramite);

    ObjectResponse procesarTraspaso(Long idTramite, TransferProcessRequest request, String bearerToken);

    ObjectResponse registrarFirma(Long idTramite, TransferSignRequest request, String bearerToken);

    /**
     * Bytes de la imagen de una firma, para servirla tal cual desde el endpoint de
     * consulta. Se sale de la envoltura {@link ObjectResponse} a proposito: el
     * consumidor es un {@code <img>}, no un cliente JSON.
     *
     * @param tipoFirma "FU" fuente o "DE" destino
     * @return los bytes, o null si el traspaso no existe o esa parte no ha firmado
     */
    byte[] obtenerFirma(Long idTramite, String tipoFirma);

    ObjectResponse recibir(Long idTramite, String bearerToken);
}
