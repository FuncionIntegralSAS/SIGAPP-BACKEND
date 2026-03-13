package com.finte.sigapp.service;


import com.finte.sigapp.dto.response.PersonResponse;
import com.finte.sigapp.model.PersonalModel;
import com.finte.sigapp.repository.PersonalRepository;
import com.finte.sigapp.service.impl.PersonalServiceImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PersonalServiceImplTest {

    @Mock
    private PersonalRepository repository; // Simulamos la base de datos

    @InjectMocks
    private PersonalServiceImpl service; // Inyectamos el mock en el servicio real

    @Test
    void buscarPorCriterios_FallaCuandoTodosLosParametrosSonNulos() {
        // Act & Assert
        ResponseStatusException exception = assertThrows(
                ResponseStatusException.class,
                () -> service.buscarPorCriterios(null, null, null)
        );

        assertEquals(HttpStatus.BAD_REQUEST, exception.getStatusCode());
        assertTrue(exception.getReason().contains("al menos un parámetro"));

        // Verificamos que NUNCA se llamó a la base de datos
        verify(repository, never()).buscarDinamica(any(), any(), any());

        System.out.println("✅ [buscarPorCriterios_FallaCuandoTodosLosParametrosSonNulos] Excepción validada: " + exception.getReason());
    }

    @Test
    void buscarPorCriterios_RetornaListaMapeadaCorrectamente() {
        // 1. Arrange (Preparar el escenario)
        PersonalModel mockModel = new PersonalModel();
        mockModel.setPersCodi("EMP01");
        mockModel.setPersDoid("112233");
        mockModel.setPersNomb("Juan");
        mockModel.setPersApel("Perez");
        mockModel.setPersDivi("DIV_ADMIN");
        mockModel.setPersEsta("ac");

        // Le decimos al mock qué devolver cuando lo llamen
        when(repository.buscarDinamica("Juan", null, null)).thenReturn(List.of(mockModel));

        // 2. Act (Ejecutar)
        List<PersonResponse> resultados = service.buscarPorCriterios("Juan", null, null);

        // 3. Assert (Verificar)
        assertNotNull(resultados);
        assertEquals(1, resultados.size());

        PersonResponse response = resultados.get(0);
        assertEquals("EMP01", response.getId());
        assertEquals("Juan Perez", response.getFullName()); // Verifica que concatenó bien
        assertEquals("DIV_ADMIN", response.getDivisionId());

        System.out.println("✅ [buscarPorCriterios_RetornaListaMapeadaCorrectamente] Mapeo de persona exitoso:");
        resultados.forEach(p -> System.out.println("   -> ID: " + p.getId() + " | Nombre: " + p.getFullName() + " | Div: " + p.getDivisionId()));
    }

    @Test
    void buscarPorCriterios_RetornaListaVacia() {
        when(repository.buscarDinamica("Pedro", null, null)).thenReturn(java.util.Collections.emptyList());

        List<PersonResponse> resultados = service.buscarPorCriterios("Pedro", null, null);

        assertNotNull(resultados);
        assertTrue(resultados.isEmpty());

        System.out.println("✅ [buscarPorCriterios_RetornaListaVacia] Manejo de lista vacía exitoso.");
    }
}