package com.finte.sigapp.controller;

import com.finte.sigapp.dto.response.BodegaDtoResponse;
import com.finte.sigapp.dto.response.BodegaResponse;
import com.finte.sigapp.service.BodegaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/bodegas")
@RequiredArgsConstructor
@Tag(name = "Bodega", description = "Endpoints para la gestión de bodegas")
public class BodegaController {

    private final BodegaService bodegaService;

    // GET /api/v1/bodegas/empresa/{empresa}
    @GetMapping("/empresa/{empresa}")
    @Operation(summary = "Obtener todas las bodegas por empresa", description = "Retorna una lista de bodegas filtradas por el código de empresa")
    @ApiResponse(responseCode = "200", description = "Lista de bodegas encontrada")
    @ApiResponse(responseCode = "204", description = "No se encontraron bodegas para la empresa proporcionada")
    public ResponseEntity<List<BodegaDtoResponse>> getByEmpresa(@PathVariable String empresa) {
        List<BodegaDtoResponse> bodegas = bodegaService.buscarTodas(empresa);

        if (bodegas.isEmpty()) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(bodegas);
    }

    // GET /api/v1/bodegas/division/D001
    @GetMapping("/division/{divisionId}")
    @Operation(summary = "Obtener bodegas por división", description = "Retorna una lista de bodegas filtradas por el código de división")
    @ApiResponse(responseCode = "200", description = "Lista de bodegas encontrada")
    @ApiResponse(responseCode = "204", description = "No se encontraron bodegas para la división proporcionada")
    public ResponseEntity<List<BodegaResponse>> getByDivision(@PathVariable String divisionId) {
        List<BodegaResponse> bodegas = bodegaService.buscarPorDivision(divisionId);

        if (bodegas.isEmpty()) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(bodegas);
    }
}