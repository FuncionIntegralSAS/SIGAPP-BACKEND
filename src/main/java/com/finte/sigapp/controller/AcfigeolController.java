package com.finte.sigapp.controller;

import com.finte.sigapp.dto.request.AcfigeolRequest;
import com.finte.sigapp.dto.response.AcfigeolActionResponse;
import com.finte.sigapp.dto.response.AcfigeolResponse;
import com.finte.sigapp.service.AcfigeolService;
import com.finte.sigapp.security.JwtTokenProvider;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/geolocalizacion-activos")
@RequiredArgsConstructor
@Tag(name = "Geolocalización de Activos Fijos", description = "Endpoints para la gestión de ubicaciones geográficas de activos fijos")
public class AcfigeolController {

    private final AcfigeolService acfigeolService;
    private final JwtTokenProvider tokenProvider;

    @PostMapping
    @Operation(summary = "Crear ubicación geográfica", description = "Crea el registro de ubicación geográfica para un activo fijo existente", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponse(responseCode = "200", description = "Ubicación geográfica creada exitosamente")
    public ResponseEntity<AcfigeolActionResponse> crear(
            @RequestHeader("Authorization") String bearerToken,
            @RequestBody AcfigeolRequest request) {
        String token = tokenProvider.extraerToken(bearerToken);
        tokenProvider.extraerIdUsuario(token); // Validación del token

        AcfigeolActionResponse response = acfigeolService.crear(request);
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{id}")
    @Operation(summary = "Actualizar ubicación geográfica", description = "Actualiza la latitud y longitud de una ubicación geográfica existente", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponse(responseCode = "200", description = "Ubicación geográfica actualizada exitosamente")
    public ResponseEntity<AcfigeolActionResponse> actualizar(
            @RequestHeader("Authorization") String bearerToken,
            @PathVariable Long id,
            @RequestBody AcfigeolRequest request) {
        String token = tokenProvider.extraerToken(bearerToken);
        tokenProvider.extraerIdUsuario(token); // Validación del token

        AcfigeolActionResponse response = acfigeolService.actualizar(id, request);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/{id}")
    @Operation(summary = "Obtener ubicación geográfica", description = "Retorna la ubicación geográfica de un activo fijo específico", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponse(responseCode = "200", description = "Ubicación geográfica encontrada")
    public ResponseEntity<AcfigeolResponse> buscarPorId(
            @Parameter(hidden = true) @RequestHeader("Authorization") String bearerToken,
            @PathVariable Long id) {
        String token = tokenProvider.extraerToken(bearerToken);
        tokenProvider.extraerIdUsuario(token); // Validación del token

        AcfigeolResponse response = acfigeolService.buscarPorId(id);
        return ResponseEntity.ok(response);
    }

    @GetMapping
    @Operation(summary = "Obtener todas las ubicaciones", description = "Retorna una lista con todas las ubicaciones geográficas registradas", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponse(responseCode = "200", description = "Lista de ubicaciones geográficas obtenida")
    @ApiResponse(responseCode = "204", description = "No se encontraron ubicaciones")
    public ResponseEntity<List<AcfigeolResponse>> buscarTodos(
            @Parameter(hidden = true) @RequestHeader("Authorization") String bearerToken) {
        String token = tokenProvider.extraerToken(bearerToken);
        tokenProvider.extraerIdUsuario(token); // Validación del token

        List<AcfigeolResponse> responseList = acfigeolService.buscarTodos();
        if (responseList.isEmpty()) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(responseList);
    }

    @DeleteMapping("/{id}")
    @Operation(summary = "Eliminar ubicación geográfica", description = "Elimina el registro de ubicación geográfica de un activo fijo", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponse(responseCode = "200", description = "Ubicación geográfica eliminada exitosamente")
    public ResponseEntity<AcfigeolActionResponse> eliminar(
            @RequestHeader("Authorization") String bearerToken,
            @PathVariable Long id) {
        String token = tokenProvider.extraerToken(bearerToken);
        tokenProvider.extraerIdUsuario(token); // Validación del token

        AcfigeolActionResponse response = acfigeolService.eliminar(id);
        return ResponseEntity.ok(response);
    }
}
