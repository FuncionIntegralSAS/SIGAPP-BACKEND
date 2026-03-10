package com.FuncionIntegral.SigoAPP.service;

import com.FuncionIntegral.SigoAPP.dto.response.BodegaResponse;
import com.FuncionIntegral.SigoAPP.model.BodegaModel;
import com.FuncionIntegral.SigoAPP.repository.BodegaRepository;
import com.FuncionIntegral.SigoAPP.service.impl.BodegaServiceImpl;
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
class BodegaServiceImplTest {

    @Mock
    private BodegaRepository repository;

    @InjectMocks
    private BodegaServiceImpl service;

    @Test
    void buscarPorDivision_RetornaListaMapeada() {
        BodegaModel mockModel = BodegaModel.builder()
                .bodeCodi("BOD01")
                .bodeDesc("Bodega Central")
                .build();

        when(repository.buscarPorDivision("DIV_1")).thenReturn(List.of(mockModel));

        List<BodegaResponse> result = service.buscarPorDivision("DIV_1");

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("BOD01", result.get(0).getId());
        assertEquals("Bodega Central", result.get(0).getName());

        System.out.println("✅ [buscarPorDivision_RetornaListaMapeada] Bodegas transformadas y mapeadas:");
        result.forEach(b -> System.out.println("   -> ID: " + b.getId() + " | Nombre: " + b.getName()));
    }

    @Test
    void buscarPorDivision_RetornaListaVaciaLocal() {
        when(repository.buscarPorDivision("DIV_INEX")).thenReturn(Collections.emptyList());

        List<BodegaResponse> result = service.buscarPorDivision("DIV_INEX");

        assertNotNull(result);
        assertEquals(0, result.size());

        System.out.println("✅ [buscarPorDivision_RetornaListaVaciaLocal] Comportamiento con Bodegas vacías validado.");
    }
}
