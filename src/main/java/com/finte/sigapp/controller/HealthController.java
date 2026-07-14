package com.finte.sigapp.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/health")
@Tag(name = "Health", description = "Endpoints para el estado de conexión del API")
public class HealthController {

    @GetMapping
    @Operation(summary = "Verificar estado de conexión", description = "Retorna OK si el API está en línea y disponible")
    @ApiResponse(responseCode = "200", description = "API en línea")
    public ResponseEntity<String> checkHealth() {
        return ResponseEntity.ok("OK");
    }
}
