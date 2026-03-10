package com.FuncionIntegral.SigoAPP.service;

import com.FuncionIntegral.SigoAPP.dto.response.ArticleResponse;
import com.FuncionIntegral.SigoAPP.mapper.ArticleMapper;
import com.FuncionIntegral.SigoAPP.model.ActiFijoModel;
import com.FuncionIntegral.SigoAPP.repository.ActiFijoRepository;
import com.FuncionIntegral.SigoAPP.service.impl.ArticleServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ArticleServiceImplTest {

    @Mock
    private ActiFijoRepository repository;

    @Mock
    private ArticleMapper mapper;

    @InjectMocks
    private ArticleServiceImpl service;

    @Test
    void obtenerAsignados_RetornaListaMapeada() {
        ActiFijoModel mockModel = new ActiFijoModel();
        ArticleResponse mockResponse = ArticleResponse.builder()
                .id("ART01")
                .licensePlate("PL-123")
                .build();

        when(repository.buscarAsignados("RESP01", "BOD01")).thenReturn(List.of(mockModel));
        when(mapper.toResponse(mockModel)).thenReturn(mockResponse);

        List<ArticleResponse> result = service.obtenerAsignados("RESP01", "BOD01");

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("ART01", result.get(0).getId());
        assertEquals("PL-123", result.get(0).getLicensePlate());

        System.out.println("[obtenerAsignados_RetornaListaMapeada] Artículos mapeados exitosamente:");
        result.forEach(a -> System.out.println("   -> ID: " + a.getId() + " | Placa: " + a.getLicensePlate()));
    }

    @Test
    void obtenerAsignados_RetornaListaVaciaLocal() {
        when(repository.buscarAsignados("RESP01", "BOD01")).thenReturn(Collections.emptyList());

        List<ArticleResponse> result = service.obtenerAsignados("RESP01", "BOD01");

        assertNotNull(result);
        assertEquals(0, result.size());

        System.out.println(
                "[obtenerAsignados_RetornaListaVaciaLocal] Lista vacía recibida desde BD manejada sin errores.");
    }
}
