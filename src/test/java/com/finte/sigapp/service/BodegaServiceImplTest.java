package com.finte.sigapp.service;

import com.finte.sigapp.dto.response.BodegaDtoResponse;
import com.finte.sigapp.dto.response.BodegaResponse;
import com.finte.sigapp.entity.BodegaEntity;
import com.finte.sigapp.model.BodegaModel;
import com.finte.sigapp.repository.BodegaJpaRepository;
import com.finte.sigapp.repository.BodegaRepository;
import com.finte.sigapp.service.impl.BodegaServiceImpl;
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

    @Mock
    private BodegaJpaRepository bodegaJpaRepository;

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

        System.out.println("[buscarPorDivision_RetornaListaMapeada] Bodegas transformadas y mapeadas:");
        result.forEach(b -> System.out.println("   -> ID: " + b.getId() + " | Nombre: " + b.getName()));
    }

    @Test
    void buscarPorDivision_RetornaListaVaciaLocal() {
        when(repository.buscarPorDivision("DIV_INEX")).thenReturn(Collections.emptyList());

        List<BodegaResponse> result = service.buscarPorDivision("DIV_INEX");

        assertNotNull(result);
        assertEquals(0, result.size());

        System.out.println("[buscarPorDivision_RetornaListaVaciaLocal] Comportamiento con Bodegas vacías validado.");
    }

    @Test
    void buscarTodas_RetornaListaMapeada() {
        // Con setters y no con el constructor posicional: BodegaEntity tiene PK
        // compuesta (bodeEmpr + bodeCodi) y cualquier campo nuevo desplazaria los
        // argumentos sin que el compilador avise.
        BodegaEntity mockEntity = new BodegaEntity();
        mockEntity.setBodeEmpr("EMP01");
        mockEntity.setBodeCodi("BOD02");
        mockEntity.setBodeDesc("Bodega Norte");
        mockEntity.setBodeEsta("AC");

        when(bodegaJpaRepository.findByBodeEmpr("EMP01")).thenReturn(List.of(mockEntity));

        List<BodegaDtoResponse> result = service.buscarTodas("EMP01");

        assertNotNull(result);
        assertEquals(1, result.size());
        assertEquals("BOD02", result.get(0).getBodeCodi());
        assertEquals("Bodega Norte", result.get(0).getBodeDesc());
        assertEquals("AC", result.get(0).getBodeEsta());
    }
}
