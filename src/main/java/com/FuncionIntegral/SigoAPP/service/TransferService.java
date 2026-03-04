package com.FuncionIntegral.SigoAPP.service;

import com.FuncionIntegral.SigoAPP.dto.request.TransferProcessRequest;
import com.FuncionIntegral.SigoAPP.dto.request.TransferRequest;
import com.FuncionIntegral.SigoAPP.dto.response.TransferResponse;

import java.util.List;

public interface TransferService {

    String crearSolicitud(TransferRequest request, String usuarioCrea);

    List<TransferResponse> listarSolicitudes(String bodegaId, String estadoFront);

    void procesarTraspaso(Long idTramite, TransferProcessRequest request, String usuarioActualiza);
}
