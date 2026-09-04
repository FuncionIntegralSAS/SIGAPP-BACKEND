package com.finte.sigapp.service.impl;

import com.finte.sigapp.dto.request.AcfigeolRequest;
import com.finte.sigapp.dto.response.AcfigeolResponse;
import com.finte.sigapp.dto.response.AcfigeolActionResponse;
import com.finte.sigapp.entity.AcfigeolEntity;
import com.finte.sigapp.exception.BussinessException;
import com.finte.sigapp.exception.catalog.ErrorCode;
import com.finte.sigapp.repository.AcfigeolJpaRepository;
import com.finte.sigapp.service.AcfigeolService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class AcfigeolServiceImpl implements AcfigeolService {

    private final AcfigeolJpaRepository repository;

    @Override
    public AcfigeolActionResponse crear(AcfigeolRequest request) {
        if (request.getAfgeIdre() == null) {
            throw new BussinessException(ErrorCode.SIGAPP_400, "El ID del activo (afgeIdre) es obligatorio para crear la ubicación.");
        }
        if (repository.existsById(request.getAfgeIdre())) {
            throw new BussinessException(ErrorCode.SIGAPP_400, "Ya existe una ubicación geográfica para el activo con ID: " + request.getAfgeIdre());
        }

        AcfigeolEntity entity = AcfigeolEntity.builder()
                .afgeIdre(request.getAfgeIdre())
                .afgeLati(request.getAfgeLati())
                .afgeLong(request.getAfgeLong())
                .build();

        repository.save(entity);
        return AcfigeolActionResponse.builder().success(true).message("Ubicación geográfica creada exitosamente").build();
    }

    @Override
    public AcfigeolActionResponse actualizar(Long id, AcfigeolRequest request) {
        AcfigeolEntity entity = repository.findById(id)
                .orElseThrow(() -> new BussinessException(ErrorCode.SIGAPP_404, "No se encontró la ubicación geográfica para el activo con ID: " + id));

        entity.setAfgeLati(request.getAfgeLati());
        entity.setAfgeLong(request.getAfgeLong());

        repository.save(entity);
        return AcfigeolActionResponse.builder().success(true).message("Ubicación geográfica actualizada exitosamente").build();
    }

    @Override
    public AcfigeolResponse buscarPorId(Long id) {
        AcfigeolEntity entity = repository.findById(id)
                .orElseThrow(() -> new BussinessException(ErrorCode.SIGAPP_404, "No se encontró la ubicación geográfica para el activo con ID: " + id));
        return mapToResponse(entity);
    }

    @Override
    public List<AcfigeolResponse> buscarTodos() {
        return repository.findAll().stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    @Override
    public AcfigeolActionResponse eliminar(Long id) {
        if (!repository.existsById(id)) {
            throw new BussinessException(ErrorCode.SIGAPP_404, "No se encontró la ubicación geográfica para el activo con ID: " + id);
        }
        repository.deleteById(id);
        return AcfigeolActionResponse.builder().success(true).message("Ubicación geográfica eliminada exitosamente").build();
    }

    private AcfigeolResponse mapToResponse(AcfigeolEntity entity) {
        return AcfigeolResponse.builder()
                .afgeIdre(entity.getAfgeIdre())
                .afgeLati(entity.getAfgeLati())
                .afgeLong(entity.getAfgeLong())
                .afgeFcre(entity.getAfgeFcre())
                .afgeUcre(entity.getAfgeUcre())
                .afgeFedi(entity.getAfgeFedi())
                .afgeUedi(entity.getAfgeUedi())
                .build();
    }
}
