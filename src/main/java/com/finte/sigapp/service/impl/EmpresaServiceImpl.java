package com.finte.sigapp.service.impl;

import com.finte.sigapp.dto.response.EmpresaDtoResponse;
import com.finte.sigapp.entity.EmpresaEntity;
import com.finte.sigapp.repository.EmpresaJpaRepository;
import com.finte.sigapp.service.EmpresaService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class EmpresaServiceImpl implements EmpresaService {

    private final EmpresaJpaRepository empresaJpaRepository;

    @Override
    public List<EmpresaDtoResponse> buscarTodas() {
        return empresaJpaRepository.buscarTodas().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    private EmpresaDtoResponse mapToResponse(EmpresaEntity entity) {
        return EmpresaDtoResponse.builder()
                .codigo(entity.getEmprCodi())
                .descripcion(entity.getEmprDesc())
                .nit(entity.getEmprNit())
                .estado(entity.getEmprEsta())
                .build();
    }
}
