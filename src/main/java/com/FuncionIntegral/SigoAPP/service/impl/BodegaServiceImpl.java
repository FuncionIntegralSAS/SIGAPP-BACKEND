package com.FuncionIntegral.SigoAPP.service.impl;

import com.FuncionIntegral.SigoAPP.dto.response.BodegaResponse;
import com.FuncionIntegral.SigoAPP.model.BodegaModel;
import com.FuncionIntegral.SigoAPP.repository.BodegaRepository;
import com.FuncionIntegral.SigoAPP.service.BodegaService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BodegaServiceImpl implements BodegaService {

    private final BodegaRepository repository;

    @Override
    public List<BodegaResponse> buscarPorDivision(String divisionId) {
        return repository.buscarPorDivision(divisionId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    private BodegaResponse mapToResponse(BodegaModel model) {
        return BodegaResponse.builder()
                .id(model.getBodeCodi())
                .name(model.getBodeDesc())
                .build();
    }
}