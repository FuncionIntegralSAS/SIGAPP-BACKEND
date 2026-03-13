package com.finte.sigapp.service;

import com.finte.sigapp.dto.request.TransferProcessRequest;
import com.finte.sigapp.dto.request.TransferRequest;
import com.finte.sigapp.dto.response.TransferResponse;

import java.util.List;

public interface TransferService {

    String crearSolicitud(TransferRequest request, String usuarioCrea);

    List<TransferResponse> listarSolicitudes(String bodegaId, String estadoFront);

    void procesarTraspaso(Long idTramite, TransferProcessRequest request, String usuarioActualiza);
}
