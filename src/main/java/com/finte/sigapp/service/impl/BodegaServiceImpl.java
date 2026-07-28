package com.finte.sigapp.service.impl;

import com.finte.sigapp.dto.response.BodegaDtoResponse;
import com.finte.sigapp.dto.response.BodegaResponse;
import com.finte.sigapp.dto.response.BodegasConteoPendientes;
import com.finte.sigapp.entity.BodegaEntity;
import com.finte.sigapp.model.BodegaModel;
import com.finte.sigapp.repository.BodegaJpaRepository;
import com.finte.sigapp.repository.BodegaRepository;
import com.finte.sigapp.service.BodegaService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BodegaServiceImpl implements BodegaService {

    private final BodegaRepository repository;
    private final BodegaJpaRepository bodegaJpaRepository;

    @Override
    public List<BodegaDtoResponse> buscarTodas(String empresa) {
        return bodegaJpaRepository.findByBodeEmpr(empresa).stream()
                .map(this::mapToDtoResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<BodegaResponse> buscarPorDivision(String divisionId) {
        return repository.buscarPorDivision(divisionId).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<BodegasConteoPendientes> bodegasConteoPendientes(String empresa) {
        return bodegaJpaRepository.findBodegasConteoActivo(empresa).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    private BodegasConteoPendientes mapToResponse(BodegaEntity entity) {
        return BodegasConteoPendientes.builder()
                .bodega(entity.getBodeCodi())
                .descripcion(entity.getBodeDesc())
                .build();
    }

    private BodegaDtoResponse mapToDtoResponse(BodegaEntity entity) {
        return BodegaDtoResponse.builder()
                .bodeCodi(entity.getBodeCodi())
                .bodeDesc(entity.getBodeDesc())
                .bodeEsta(entity.getBodeEsta())
                .build();
    }

    private BodegaResponse mapToResponse(BodegaModel model) {
        return BodegaResponse.builder()
                .id(model.getBodeCodi())
                .name(model.getBodeDesc())
                .build();
    }
}