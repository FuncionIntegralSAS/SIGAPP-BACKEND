package com.finte.sigapp.controller;

import com.finte.sigapp.dto.response.ObjectResponse;
import com.finte.sigapp.service.ServidorInfoService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/health")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Health", description = "Endpoints para el estado de conexión del API")
public class HealthController {

    private static final String MSG_ERROR = "Error al realizar el proceso:";

    private final ServidorInfoService servidorInfoService;

    @GetMapping
    @Operation(summary = "Verificar estado de conexión", description = "Retorna OK si el API está en línea y disponible")
    @ApiResponse(responseCode = "200", description = "API en línea")
    public ResponseEntity<String> checkHealth() {
        return ResponseEntity.ok("OK");
    }

    /**
     * Ruta aparte y no un cambio en la de arriba: aquella devuelve el texto
     * "OK" y hay quien ya la consume asi. Esta consulta la base; la otra no.
     *
     * Es publica (cae en /api/v1/health/** de SecurityConfig) porque la app la
     * llama antes de tener token: es lo primero que hace con una URL recien
     * leida de un QR.
     */
    @GetMapping("/info")
    @Operation(summary = "Identidad del backend",
               description = "Aplicación y URL pública configurada. La app móvil la usa para validar la URL leída del QR")
    public ResponseEntity<ObjectResponse> info() {

        ObjectResponse response = new ObjectResponse();
        try {
            response = servidorInfoService.consultarInfo();
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error consultando la identidad del backend: {}", e.getMessage(), e);
            response.setCode(-1);
            response.setMsg(MSG_ERROR + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }
}
