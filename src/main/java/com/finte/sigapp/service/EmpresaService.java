package com.finte.sigapp.service;

import com.finte.sigapp.dto.response.EmpresaDtoResponse;

import java.util.List;

public interface EmpresaService {
    List<EmpresaDtoResponse> buscarTodas();
}
