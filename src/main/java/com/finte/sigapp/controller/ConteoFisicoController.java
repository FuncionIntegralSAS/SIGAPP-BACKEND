package com.finte.sigapp.controller;

import com.finte.sigapp.dto.request.ConteoFisicoRequest;
import com.finte.sigapp.dto.response.ConteoFisicoResponse;
import com.finte.sigapp.service.ConteoFisicoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/conteo-fisico")
@RequiredArgsConstructor
@Tag(name = "Conteo Físico", description = "API para gestionar los conteos físicos")
public class ConteoFisicoController {

    private final ConteoFisicoService conteoFisicoService;

    @Operation(summary = "Registrar un conteo físico", description = "Procesa y registra un nuevo conteo físico consumiendo el procedimiento PL/SQL.")
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Conteo registrado exitosamente", content = @Content(schema = @Schema(implementation = ConteoFisicoResponse.class))),
            @ApiResponse(responseCode = "400", description = "Error en la solicitud o validación del conteo", content = @Content(schema = @Schema(implementation = ConteoFisicoResponse.class)))
    })
    @PostMapping
    public ResponseEntity<ConteoFisicoResponse> registrarConteo(
            @Valid @RequestBody ConteoFisicoRequest request) {

        ConteoFisicoResponse response = conteoFisicoService.procesarConteoFisico(request);

        if (response.isSuccess()) {
            return ResponseEntity.ok(response);
        }

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }
}