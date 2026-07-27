package com.finte.sigapp.service;

import com.finte.sigapp.dto.response.ArticleResponse;
import com.finte.sigapp.mapper.ArticleMapper;
import com.finte.sigapp.model.ActiFijoModel;
import com.finte.sigapp.repository.ActiFijoRepository;
import com.finte.sigapp.service.impl.ArticleServiceImpl;
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
                .build();

        when(repository.buscarAsignados("RESP01", "BOD01")).thenReturn(List.of(mockModel));
        when(mapper.toResponse(mockModel)).thenReturn(mockResponse);

        List<ArticleResponse> result = service.obtenerAsignados("RESP01", "BOD01");

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("ART01", result.get(0).getId());

        System.out.println("[obtenerAsignados_RetornaListaMapeada] Artículos mapeados exitosamente:");
        result.forEach(a -> System.out.println("   -> ID: " + a.getId()));
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
