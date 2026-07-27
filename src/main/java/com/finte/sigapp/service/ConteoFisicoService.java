package com.finte.sigapp.service;

import com.finte.sigapp.dto.request.CierreConteoRequest;
import com.finte.sigapp.dto.request.ConteoFisicoRequest;
import com.finte.sigapp.dto.response.ConteoFisicoResponse;

public interface ConteoFisicoService {
    ConteoFisicoResponse generarConteoFisico(ConteoFisicoRequest request);

    ConteoFisicoResponse cerrarConteo(String bearerToken, CierreConteoRequest request);
}
