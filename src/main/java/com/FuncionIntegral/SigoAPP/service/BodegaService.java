package com.FuncionIntegral.SigoAPP.service;

import com.FuncionIntegral.SigoAPP.dto.response.BodegaResponse;

import java.util.List;

public interface BodegaService {
    List<BodegaResponse> buscarPorDivision(String divisionId);
}