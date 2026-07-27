package com.finte.sigapp.controller;

import com.finte.sigapp.dto.response.PendienteArticuloResponse;
import com.finte.sigapp.exception.ErrorResponse;
import com.finte.sigapp.service.PendienteArticulosService;

import com.finte.sigapp.dto.request.AsignacionConteoRequest;
import com.finte.sigapp.dto.request.ConteoFisicoRequest;
import com.finte.sigapp.dto.request.ReporteConteoRequest;
import com.finte.sigapp.dto.request.CierreConteoRequest;
import com.finte.sigapp.dto.response.ConteoFisicoResponse;
import com.finte.sigapp.service.ConteoFisicoService;
import com.finte.sigapp.service.FicofiarasService;
import com.finte.sigapp.security.JwtTokenProvider;
import org.springframework.web.bind.annotation.RequestHeader;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import java.util.List;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
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
            @ApiResponse(responseCode = "400", description = "Error en la solicitud o validación del conteo", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
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
            @ApiResponse(responseCode = "400", description = "Error en la solicitud o validación del conteo", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/asignar_articulos")
    public ResponseEntity<ConteoFisicoResponse> asignarArticulos(@Valid @RequestBody AsignacionConteoRequest request) {
        log.info("Controller AsignarArticulo ");
        ficofiarasService.asignarArticulos(request);

        return ResponseEntity.ok(ConteoFisicoResponse.builder().success(true).message("OK SERVICIO").build());

    }

    @Operation(summary = "Obtener artículos pendientes", description = "Retorna la lista de artículos pendientes para cache offline.", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Lista obtenida", content = @Content(schema = @Schema(implementation = PendienteArticuloResponse.class))),
            @ApiResponse(responseCode = "500", description = "Error interno", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @GetMapping("/pendientes")
    public ResponseEntity<List<PendienteArticuloResponse>> obtenerPendientes(
            @Parameter(hidden = true) @RequestHeader("Authorization") String bearerToken) throws Exception {

        String token = tokenProvider.extraerToken(bearerToken);
        Long userId = tokenProvider.extraerIdUsuario(token);
        List<PendienteArticuloResponse> lista = pendienteArticulosService.obtenerPendientesPorUsuario(userId);

        return ResponseEntity.ok(lista);
    }

    @Operation(summary = "Reportar conteo físico", description = "Actualiza la cantidad por articulos del conteo fisico", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Conteo reportado exitosamente", content = @Content(schema = @Schema(implementation = ConteoFisicoResponse.class))),
            @ApiResponse(responseCode = "403", description = "Usuario no autorizado", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/reportar")
    public ResponseEntity<ConteoFisicoResponse> reportarConteo(@RequestHeader("Authorization") String bearerToken,
            @RequestBody ReporteConteoRequest request) {

        String token = tokenProvider.extraerToken(bearerToken);
        Long userId = tokenProvider.extraerIdUsuario(token);

        return ResponseEntity.ok(ficofiarasService.reportarConteo(userId, request));
    }

    @Operation(summary = "Cerrar conteo físico", description = "Cierra un conteo físico para una bodega actualizando su estado", security = @SecurityRequirement(name = "bearerAuth"))
    @ApiResponses(value = {
            @ApiResponse(responseCode = "200", description = "Conteo cerrado exitosamente", content = @Content(schema = @Schema(implementation = ConteoFisicoResponse.class))),
            @ApiResponse(responseCode = "400", description = "Error en la solicitud", content = @Content(schema = @Schema(implementation = ErrorResponse.class))),
            @ApiResponse(responseCode = "403", description = "Usuario no autorizado", content = @Content(schema = @Schema(implementation = ErrorResponse.class)))
    })
    @PostMapping("/cerrar")
    public ResponseEntity<ConteoFisicoResponse> cerrarConteo(@RequestHeader("Authorization") String bearerToken,
            @Valid @RequestBody CierreConteoRequest request) {

        ConteoFisicoResponse response = conteoFisicoService.cerrarConteo(bearerToken, request);

        if (response.isSuccess()) {
            return ResponseEntity.ok(response);
        }

        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
    }
}
