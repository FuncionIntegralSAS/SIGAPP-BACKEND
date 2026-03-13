package com.finte.sigapp.controller;

import com.finte.sigapp.dto.response.EmpresaDtoResponse;
import com.finte.sigapp.service.EmpresaService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/empresas")
@RequiredArgsConstructor
@Tag(name = "Empresa", description = "Endpoints para la gestión de empresas")
public class EmpresaController {

    private final EmpresaService empresaService;

    // GET /api/v1/empresas
    @GetMapping("/getAll")
    @Operation(summary = "Obtener todas las empresas", description = "Retorna una lista de todas las empresas registradas")
    @ApiResponse(responseCode = "200", description = "Lista de empresas encontrada")
    @ApiResponse(responseCode = "204", description = "No se encontraron empresas")
    public ResponseEntity<List<EmpresaDtoResponse>> getAll() {
        List<EmpresaDtoResponse> empresas = empresaService.buscarTodas();

        if (empresas.isEmpty()) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(empresas);
    }
}
