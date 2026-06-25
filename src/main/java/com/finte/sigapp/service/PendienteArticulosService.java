package com.finte.sigapp.service;

import com.finte.sigapp.dto.response.PendienteArticuloResponse;
import java.util.List;

public interface PendienteArticulosService {
    List<PendienteArticuloResponse> obtenerPendientes();
    List<PendienteArticuloResponse> obtenerPendientesPorUsuario(Long idUsuario);
}
