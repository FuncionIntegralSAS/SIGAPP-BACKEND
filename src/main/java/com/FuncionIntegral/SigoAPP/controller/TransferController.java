package com.FuncionIntegral.SigoAPP.controller;

import com.FuncionIntegral.SigoAPP.dto.request.TransferProcessRequest;
import com.FuncionIntegral.SigoAPP.dto.request.TransferRequest;
import com.FuncionIntegral.SigoAPP.dto.response.TransferResponse;
import com.FuncionIntegral.SigoAPP.service.TransferService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/traspasos")
@RequiredArgsConstructor
public class TransferController {

    private final TransferService transferService;

    // POST: Crear Solicitud
    @PostMapping
    public ResponseEntity<?> createTransfer(@Valid @RequestBody TransferRequest request) {
        // TODO: Reemplazar por usuario real del Token JWT cuando esté activo
        String usuarioCrea = "USER_APP";

        String nuevoId = transferService.crearSolicitud(request, usuarioCrea);

        // Retornamos JSON simple: { "success": true, "id": "12345" }
        return ResponseEntity.ok(Map.of("success", true, "id", nuevoId));
    }

    // GET: Listar Solicitudes (Bandeja de Entrada)
    // ?bodega=B001&estado=pe (Pendientes)
    // ?bodega=B001&estado=pr (Procesados: Aprobados + Rechazados)
    @GetMapping
    public ResponseEntity<List<TransferResponse>> listRequests(
            @RequestParam String bodega,
            @RequestParam String estado
    ) {
        List<TransferResponse> lista = transferService.listarSolicitudes(bodega, estado);

        if (lista.isEmpty()) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(lista);
    }

    // 3. Procesar Solicitud (Aprobar o Rechazar)
    @PutMapping("/{id}/procesar")
    public ResponseEntity<?> processTransfer(
            @PathVariable Long id,
            @Valid @RequestBody TransferProcessRequest request
    ) {
        // TODO: Extraer el código de la tabla PERSONAL del usuario autenticado vía JWT
        // Por ahora, usamos un código duro que no supere los 12 caracteres (como lo pide el SP)
        String usuarioActualiza = "USER_PER_01";

        transferService.procesarTraspaso(id, request, usuarioActualiza);

        return ResponseEntity.ok(
                Map.of(
                        "success", true,
                        "message", "El trámite fue procesado correctamente"
                )
        );
    }
}