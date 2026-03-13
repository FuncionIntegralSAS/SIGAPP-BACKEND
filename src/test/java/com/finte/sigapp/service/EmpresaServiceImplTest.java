package com.finte.sigapp.service;

import com.finte.sigapp.dto.response.EmpresaDtoResponse;
import com.finte.sigapp.entity.EmpresaEntity;
import com.finte.sigapp.repository.EmpresaJpaRepository;
import com.finte.sigapp.service.impl.EmpresaServiceImpl;
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
class EmpresaServiceImplTest {

    @Mock
    private EmpresaJpaRepository empresaJpaRepository;

    @InjectMocks
    private EmpresaServiceImpl empresaService;

    @Test
    void buscarTodas_RetornaListaMapeada() {
        EmpresaEntity mockEntity = new EmpresaEntity("EMP01", "Empresa Test", "123456789", "AC");

        when(empresaJpaRepository.buscarTodas()).thenReturn(List.of(mockEntity));

        List<EmpresaDtoResponse> result = empresaService.buscarTodas();

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("EMP01", result.get(0).getCodigo());
        assertEquals("Empresa Test", result.get(0).getDescripcion());
        assertEquals("123456789", result.get(0).getNit());
        assertEquals("AC", result.get(0).getEstado());
    }

    @Test
    void buscarTodas_RetornaListaVacia() {
        when(empresaJpaRepository.buscarTodas()).thenReturn(Collections.emptyList());

        List<EmpresaDtoResponse> result = empresaService.buscarTodas();

        assertNotNull(result);
        assertEquals(0, result.size());
    }
}
