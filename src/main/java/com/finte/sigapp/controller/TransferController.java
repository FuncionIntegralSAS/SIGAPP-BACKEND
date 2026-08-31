package com.finte.sigapp.controller;

import com.finte.sigapp.dto.request.TransferProcessRequest;
import com.finte.sigapp.dto.request.TransferRequest;
import com.finte.sigapp.dto.request.TransferSignRequest;
import com.finte.sigapp.dto.response.ObjectListResponse;
import com.finte.sigapp.dto.response.ObjectResponse;
import com.finte.sigapp.exception.BussinessException;
import com.finte.sigapp.service.TransferService;
import com.finte.sigapp.utils.FirmaUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/traspasos")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "Traspasos", description = "Gestion de traspasos de activos")
public class TransferController {

    private static final String MSG_ERROR = "Error al realizar el proceso:";

    private final TransferService transferService;

    @Operation(summary = "Crear traspaso", description = "Registra un traspaso en estado pendiente")
    @PostMapping("/crear")
    public ResponseEntity<ObjectResponse> createTransfer(@Parameter(hidden = true) @RequestHeader("Authorization") String bearerToken,
                                                         @Valid @RequestBody TransferRequest request) {

        ObjectResponse response = new ObjectResponse();
        try {
            response = transferService.crearSolicitud(request, bearerToken);
            return ResponseEntity.ok(response);

        } catch (Exception e) {

            log.error("Error creando el traspaso: {}", e.getMessage(), e);
            response.setCode(-1);
            response.setMsg(MSG_ERROR + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * Bandeja de traspasos.
     *
     * @param estado "pe" pendientes, "pr" procesados (ap, na, af, ad, re) o un
     *               estado puntual de FI_MOVITRAS
     */
    @Operation(summary = "Listar traspasos", description = "Bandeja filtrada por estado, con empresa y bodega opcionales")
    @GetMapping("/list")
    public ResponseEntity<ObjectListResponse> listRequests(@Parameter(hidden = true) @RequestHeader("Authorization") String bearerToken,
                                                           @RequestParam String estado,
                                                           @RequestParam(required = false) String empresa,
                                                           @RequestParam(required = false) String bodega) {

        ObjectListResponse response = new ObjectListResponse();
        try {

            response = transferService.listarSolicitudes(empresa, bodega, estado);
            return ResponseEntity.ok(response);
        } catch (Exception e) {

            log.error("Error listando traspasos: {}", e.getMessage(), e);
            response.setCode(-1);
            response.setMsg(MSG_ERROR + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    @Operation(summary = "Detalle de un traspaso", description = "Incluye las firmas en base64")
    @GetMapping("get/{id}")
    public ResponseEntity<ObjectResponse> getTransfer(@PathVariable Long id) {

        ObjectResponse response = new ObjectResponse();
        try {
            response = transferService.obtenerDetalle(id);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error consultando el traspaso {}: {}", id, e.getMessage(), e);
            response.setCode(-1);
            response.setMsg(MSG_ERROR + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    @Operation(summary = "Aprobar o rechazar", description = "Mueve el traspaso a 'ap' o 'na'")
    @PostMapping("process/{id}")
    public ResponseEntity<ObjectResponse> processTransfer(@Parameter(hidden = true) @RequestHeader("Authorization") String bearerToken,
                                                          @PathVariable Long id,
                                                          @Valid @RequestBody TransferProcessRequest request) {

        ObjectResponse response = new ObjectResponse();
        try {
            response = transferService.procesarTraspaso(id, request, bearerToken);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error procesando el traspaso {}: {}", id, e.getMessage(), e);
            response.setCode(-1);
            response.setMsg(MSG_ERROR + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    @Operation(summary = "Registrar firma", description = "Guarda la firma de la fuente (FU) o del destino (DE)")
    @PutMapping("sign/{id}")
    public ResponseEntity<ObjectResponse> signTransfer(@Parameter(hidden = true) @RequestHeader("Authorization") String bearerToken,
                                                       @PathVariable Long id,
                                                       @Valid @RequestBody TransferSignRequest request) {

        ObjectResponse response = new ObjectResponse();
        try {
            response = transferService.registrarFirma(id, request, bearerToken);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error registrando la firma del traspaso {}: {}", id, e.getMessage(), e);
            response.setCode(-1);
            response.setMsg(MSG_ERROR + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }

    /**
     * Devuelve la firma como imagen, no dentro de la envoltura JSON: sirve para
     * verificarla desde el navegador o para pintarla en el front sin arrastrar el
     * base64 en el detalle.
     *
     * La ruta esta bajo /api/v1/traspasos, asi que exige el token ADMIN como el
     * resto del modulo. Un &lt;img src&gt; no manda el header Authorization: el front
     * debe traerla con fetch y construir un object URL.
     */
    @Operation(summary = "Ver la firma", description = "Devuelve la imagen de la firma (FU fuente, DE destino)")
    @GetMapping("sign/{id}/{tipo}")
    public ResponseEntity<byte[]> getSignature(@Parameter(hidden = true) @RequestHeader("Authorization") String bearerToken,
                                               @PathVariable Long id,
                                               @PathVariable String tipo) {

        try {
            byte[] firma = transferService.obtenerFirma(id, tipo);

            if (firma == null || firma.length == 0) {
                return ResponseEntity.notFound().build();
            }

            return ResponseEntity.ok()
                    .contentType(FirmaUtil.mime(firma))
                    // Es una firma manuscrita: no debe quedar en cache del navegador
                    // ni de un proxy intermedio.
                    .header(HttpHeaders.CACHE_CONTROL, "no-store")
                    .body(firma);

        } catch (BussinessException e) {
            log.warn("Peticion invalida de firma para el traspaso {}: {}", id, e.getMessage());
            return ResponseEntity.badRequest().build();

        } catch (Exception e) {
            log.error("Error consultando la firma {} del traspaso {}: {}", tipo, id, e.getMessage(), e);
            return ResponseEntity.status(500).build();
        }
    }

    @Operation(summary = "Registrar recepción", description = "Sella la fecha de recibido y cierra el traspaso")
    @PutMapping("/recibir/{id}")
    public ResponseEntity<ObjectResponse> receiveTransfer(@Parameter(hidden = true) @RequestHeader("Authorization") String bearerToken,
                                                          @PathVariable Long id) {

        ObjectResponse response = new ObjectResponse();
        try {
            response = transferService.recibir(id, bearerToken);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error registrando la recepcion del traspaso {}: {}", id, e.getMessage(), e);
            response.setCode(-1);
            response.setMsg(MSG_ERROR + e.getMessage());
            return ResponseEntity.status(500).body(response);
        }
    }
}
