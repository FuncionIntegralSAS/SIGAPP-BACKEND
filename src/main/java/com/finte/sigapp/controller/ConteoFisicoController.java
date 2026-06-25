package com.finte.sigapp.controller;

import com.finte.sigapp.dto.response.PendienteArticuloResponse;
import com.finte.sigapp.service.PendienteArticulosService;

import com.finte.sigapp.dto.request.AsignacionConteoRequest;
import com.finte.sigapp.dto.request.ConteoFisicoRequest;
import com.finte.sigapp.dto.response.ConteoFisicoResponse;
import com.finte.sigapp.service.ConteoFisicoService;
import com.finte.sigapp.service.FicofiarasService;
import com.finte.sigapp.security.JwtTokenProvider;
import org.springframework.web.bind.annotation.RequestHeader;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.bind.annotation.GetMapping;

@RestController
@RequestMapping("/api/v1/conteo-fisico")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Conteo Físico", description = "API para gestionar los conteos físicos")
public class ConteoFisicoController {

    private final ConteoFisicoService conteoFisicoService;
    private final FicofiarasService ficofiarasService;
    private final PendienteArticulosService pendienteArticulosService;
    private final JwtTokenProvider tokenProvider;

    @Operation(summary = "Registrar un conteo físico", description = "Procesa y registra un nuevo conteo físico consumiendo el procedimiento PL/SQL.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Conteo registrado exitosamente", content = @Content(schema = @Schema(implementation = ConteoFisicoResponse.class))),
            @ApiResponse(responseCode = "400", description = "Error en la solicitud o validación del conteo", content = @Content(schema = @Schema(implementation = ConteoFisicoResponse.class)))
    })
    @PostMapping("/registrar")
    public ResponseEntity<ConteoFisicoResponse> registrarConteo(@Valid @RequestBody ConteoFisicoRequest request) {

        ConteoFisicoResponse response = conteoFisicoService.generarConteoFisico(request);

        if (response.isSuccess()) {
            return ResponseEntity.ok(response);
        }

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }

    @Operation(summary = "Asignar articulos del conteo físico", description = "Consulta los articulos de la bodega a contar y los asigna a los repsonsables")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Asignación de articulos exitosa", content = @Content(schema = @Schema(implementation = ConteoFisicoResponse.class))),
            @ApiResponse(responseCode = "400", description = "Error en la solicitud o validación del conteo", content = @Content(schema = @Schema(implementation = ConteoFisicoResponse.class)))
    })
    @PostMapping("/asignar_articulos")
    public ResponseEntity<ConteoFisicoResponse> asignarArticulos(@Valid @RequestBody AsignacionConteoRequest request) {
        log.info("Controller AsignarArticulo ");
        ficofiarasService.asignarArticulos(request);

        return ResponseEntity.ok(ConteoFisicoResponse.builder().success(true).message("OK SERVICIO").build());

    }

    @Operation(summary = "Obtener artículos pendientes", description = "Retorna la lista de artículos pendientes para cache offline.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Lista obtenida", content = @Content(schema = @Schema(implementation = PendienteArticuloResponse.class))),
            @ApiResponse(responseCode = "500", description = "Error interno")
    })
    @GetMapping("/pendientes")
    public ResponseEntity<List<PendienteArticuloResponse>> obtenerPendientes(
            @RequestHeader("Authorization") String bearerToken) {
        // Extract token
        String token;
        try {
            token = tokenProvider.extraerToken(bearerToken);
        } catch (Exception e) {
            log.error("Error extracting token", e);
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        log.info("bearerToken = {}", bearerToken);
        // Validate token
        if (token == null || !tokenProvider.validarToken(token)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        Long idUsuario1;
        try {
            idUsuario1 = tokenProvider.extraerIdUsuario(token);
        } catch (Exception e) {
            log.error("Error extracting user ID from token", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).build();
        }

        List<PendienteArticuloResponse> lista = pendienteArticulosService.obtenerPendientesPorUsuario(idUsuario1);
        return ResponseEntity.ok(lista);
    }

}
