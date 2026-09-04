package com.finte.sigapp.service;

import com.finte.sigapp.dto.request.AcfigeolRequest;
import com.finte.sigapp.dto.response.AcfigeolResponse;

import com.finte.sigapp.dto.response.AcfigeolActionResponse;

import java.util.List;

public interface AcfigeolService {
    AcfigeolActionResponse crear(AcfigeolRequest request);
    AcfigeolActionResponse actualizar(Long id, AcfigeolRequest request);
    AcfigeolResponse buscarPorId(Long id);
    List<AcfigeolResponse> buscarTodos();
    AcfigeolActionResponse eliminar(Long id);
}
