package com.FuncionIntegral.SigoAPP.service;

import com.FuncionIntegral.SigoAPP.dto.request.ConteoFisicoRequest;
import com.FuncionIntegral.SigoAPP.dto.response.ConteoFisicoResponse;

public interface ConteoFisicoService {
    ConteoFisicoResponse procesarConteoFisico(ConteoFisicoRequest request);
}
