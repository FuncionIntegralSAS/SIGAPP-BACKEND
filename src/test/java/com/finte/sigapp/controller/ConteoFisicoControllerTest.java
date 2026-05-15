package com.finte.sigapp.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finte.sigapp.dto.request.AsignacionConteoRequest;
import com.finte.sigapp.dto.request.ConteoFisicoRequest;
import com.finte.sigapp.dto.response.ConteoFisicoResponse;
import com.finte.sigapp.security.JwtTokenProvider;
import com.finte.sigapp.service.ConteoFisicoService;
import com.finte.sigapp.service.FicofiarasService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.shadow.com.univocity.parsers.annotations.Nested;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;

import static org.assertj.core.api.AssertionsForClassTypes.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(
        controllers = ConteoFisicoController.class,
        excludeAutoConfiguration = {
                org.springframework.boot.autoconfigure.security.servlet.SecurityAutoConfiguration.class
        }
)
@AutoConfigureMockMvc(addFilters = false)
class ConteoFisicoControllerTest {

    private static final String BASE_URL = "/api/v1/conteo-fisico";
    private static final String REGISTRAR_URL = BASE_URL + "/registrar";
    private static final String ASIGNAR_URL = BASE_URL + "/asignar_articulos";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ConteoFisicoService conteoFisicoService;

    @MockitoBean
    private FicofiarasService ficofiarasService;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @Test
    @DisplayName("Debe retornar 200 OK cuando el conteo físico se registra exitosamente")
    @Nested
    void shouldReturnOkWhenRegistrarConteoIsSuccessful() throws Exception {
        // Arrange
        ConteoFisicoRequest request =  buildValidConteoFisicoRequest();

        ConteoFisicoResponse response = ConteoFisicoResponse.builder()
                .success(true)
                .message("Conteo registrado exitosamente")
                .build();

        when(conteoFisicoService.generarConteoFisico(any(ConteoFisicoRequest.class)))
                .thenReturn(response);

        // Act & Assert
        mockMvc.perform(post(REGISTRAR_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message")
                        .value("Conteo registrado exitosamente"));

        verify(conteoFisicoService, times(1))
                .generarConteoFisico(any(ConteoFisicoRequest.class));
    }

    @Test
    @DisplayName("Debe retornar 400 Bad Request cuando el servicio indica error")
    @Nested
    void shouldReturnBadRequestWhenRegistrarConteoFails() throws Exception {
        // Arrange
        ConteoFisicoRequest request = buildValidConteoFisicoRequest();

        ConteoFisicoResponse response = ConteoFisicoResponse.builder()
                .success(false)
                .message("Error al registrar conteo")
                .build();

        when(conteoFisicoService.generarConteoFisico(any(ConteoFisicoRequest.class)))
                .thenReturn(response);

        // Act & Assert
        mockMvc.perform(post(REGISTRAR_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message")
                        .value("Error al registrar conteo"));

        verify(conteoFisicoService).generarConteoFisico(any(ConteoFisicoRequest.class));
    }

    @Test
    @DisplayName("Debe retornar 200 OK cuando se asignan artículos correctamente")
    @Nested
    void shouldReturnOkWhenAsignarArticulosIsSuccessful() throws Exception {
        // Arrange
        AsignacionConteoRequest request = new AsignacionConteoRequest();

        // Act & Assert
        mockMvc.perform(post(ASIGNAR_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.message").value("OK SERVICIO"));

        verify(ficofiarasService, times(1))
                .asignarArticulos(any(AsignacionConteoRequest.class));
    }

    @Test
    @DisplayName("Debe enviar al servicio el mismo request recibido")
    @Nested
    void shouldPassRequestToFicofiarasService() throws Exception {
        // Arrange
        AsignacionConteoRequest request = new AsignacionConteoRequest();

        // Act
        mockMvc.perform(post(ASIGNAR_URL)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        // Assert
        ArgumentCaptor<AsignacionConteoRequest> captor =
                ArgumentCaptor.forClass(AsignacionConteoRequest.class);

        verify(ficofiarasService).asignarArticulos(captor.capture());
        // Verifica que todos los atributos del objeto recibido por el servicio
        // sean iguales a los del objeto enviado en la petición.
        assertThat(captor.getValue())
                .usingRecursiveComparison()
                .isEqualTo(request);
    }

    private ConteoFisicoRequest buildValidConteoFisicoRequest() {
        ConteoFisicoRequest request = new ConteoFisicoRequest();
        request.setEmpresa("01");
        request.setBodega("001");
        request.setArticulo("ART001");
        // Se recomienda usar una fecha fija para que la prueba sea determinística
        request.setFecha(LocalDateTime.of(2026, 1, 1, 8, 0));
        request.setVerificarExistencia("N");
        return request;
    }
}