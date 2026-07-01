package com.finte.sigapp.service;

import com.finte.sigapp.dto.request.AsignacionConteoRequest;
import com.finte.sigapp.dto.request.ReporteConteoRequest;
import com.finte.sigapp.dto.response.ConteoFisicoResponse;

public interface FicofiarasService {
    ConteoFisicoResponse reportarConteo(Long userId, ReporteConteoRequest request);

    ConteoFisicoResponse asignarArticulos(AsignacionConteoRequest request);
}
