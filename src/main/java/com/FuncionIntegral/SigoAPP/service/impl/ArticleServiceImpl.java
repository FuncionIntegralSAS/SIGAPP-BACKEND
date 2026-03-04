package com.FuncionIntegral.SigoAPP.service.impl;

import com.FuncionIntegral.SigoAPP.dto.response.ArticleResponse;
import com.FuncionIntegral.SigoAPP.mapper.ArticleMapper;
import com.FuncionIntegral.SigoAPP.repository.ActiFijoRepository;
import com.FuncionIntegral.SigoAPP.service.ArticleService;
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
}