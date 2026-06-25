package com.finte.sigapp.service.impl;

import com.finte.sigapp.dto.response.ArticleResponse;
import com.finte.sigapp.mapper.ArticleMapper;
import com.finte.sigapp.repository.ActiFijoRepository;
import com.finte.sigapp.service.ArticleService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor // Genera el constructor para la inyección de dependencias (Lombok)
public class ArticleServiceImpl implements ArticleService {

    private final ActiFijoRepository repository;
    private final ArticleMapper mapper;

    @Override
    public List<ArticleResponse> obtenerAsignados(String idResponsable, String idBodega) {
        // Pasamos los parámetros tal cual al repositorio
        return repository.buscarAsignados(idResponsable, idBodega)
                .stream()
                .map(mapper::toResponse)
                .collect(Collectors.toList());
    }

    @Override
    public List<ArticleResponse> obtenerAsignadosPorBodega(String bodega) {
        return repository.buscarAsignadosPorBodega(bodega)
                .stream()
                .map(mapper::toResponse)
                .collect(Collectors.toList());
    }
}