package com.finte.sigapp.service;

import com.finte.sigapp.dto.response.BodegaDtoResponse;
import com.finte.sigapp.dto.response.BodegaResponse;
import com.finte.sigapp.dto.response.BodegasConteoPendientes;

import java.util.List;

public interface BodegaService {
    List<BodegaResponse> buscarPorDivision(String divisionId);

    List<BodegaDtoResponse> buscarTodas(String empresa);

    List<BodegasConteoPendientes> bodegasConteoPendientes(String empresa);
}