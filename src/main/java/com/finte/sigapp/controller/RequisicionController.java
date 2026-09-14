package com.finte.sigapp.controller;

import com.finte.sigapp.dto.request.RequisicionAnularRequest;
import com.finte.sigapp.dto.request.RequisicionFirmaRequest;
import com.finte.sigapp.dto.request.RequisicionLineasRequest;
import com.finte.sigapp.dto.request.RequisicionRegistrarRequest;
import com.finte.sigapp.dto.response.ObjectListResponse;
import com.finte.sigapp.dto.response.ObjectResponse;
import com.finte.sigapp.exception.BussinessException;
import com.finte.sigapp.service.RequisicionService;
import com.finte.sigapp.utils.FirmaUtil;
import com.finte.sigapp.utils.OracleErrorUtil;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Pattern;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

/**
 * Requisiciones de suministro.
 *
 * La requisicion se identifica por la TERNA (empresa, tipo de documento,
 * numero), que es la llave que ya tiene en el ERP. No se inventa un
 * identificador propio: la requisicion no la crea este modulo, asi que un id
 * sustituto obligaria a mantener una tabla de traduccion sin ganancia.
 *
 * Que {@code numero} se declare como {@code Long} tiene un efecto de seguridad
 * y no solo de tipado: el procedimiento del ERP que genera la salida filtra las
 * lineas con LIKE sobre ese valor, de modo que un comodin barreria otras
 * requisiciones. Al exigir un entero, Spring rechaza con 400 cualquier cosa que
 * no lo sea antes de que llegue al service.
 *
 * Las transiciones usan PUT y no POST porque actuan sobre un recurso que ya
 * existe: ninguna crea nada.
 */
@RestController
@RequestMapping("/api/v1/requisiciones")
@RequiredArgsConstructor
@Validated
@Slf4j
@Tag(name = "Requisiciones", description = "Ciclo de vida de las requisiciones de suministro")
public class RequisicionController {

    private static final String MSG_ERROR = "Error al realizar el proceso:";

    private static final String PATRON_EMPRESA = "^[A-Za-z0-9]{1,6}$";
    private static final String PATRON_TIPODOCU = "^[A-Za-z0-9]{1,4}$";

    private final RequisicionService requisicionService;

    @Operation(summary = "Tipos gestionables",
               description = "Tipos de requisicion habilitados. Si viene vacio, falta cargar la equivalencia en EQUIVALE")
    @GetMapping("/tipos")
    public ResponseEntity<ObjectListResponse> listTypes() {

        ObjectListResponse response = new ObjectListResponse();
        try {
            response = requisicionService.listarTipos();
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error listando los tipos de requisicion: {}", e.getMessage(), e);
            return error(response, e);
        }
    }

    /**
     * Bandeja: una fila por requisicion, no por articulo.
     *
     * @param estado estado derivado del documento: in, ap, en, rg o ae
     * @param desde  opcional. Sin el se aplica la ventana por defecto, porque
     *               MOVIRESU tiene mas de cien mil lineas registradas
     */
    @Operation(summary = "Listar requisiciones",
               description = "Bandeja filtrada por estado, con empresa, tipo, bodega y fecha opcionales")
    @GetMapping
    public ResponseEntity<ObjectListResponse> listRequisitions(
            @Parameter(hidden = true) @RequestHeader("Authorization") String bearerToken,
            @RequestParam(required = false) String estado,
            @RequestParam(required = false) String empresa,
            @RequestParam(required = false) String tipoDocumento,
            @RequestParam(required = false) String bodega,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate desde) {

        ObjectListResponse response = new ObjectListResponse();
        try {
            response = requisicionService.listarRequisiciones(estado, empresa, tipoDocumento, bodega, desde);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error listando requisiciones: {}", e.getMessage(), e);
            return error(response, e);
        }
    }

    @Operation(summary = "Detalle de una requisicion",
               description = "Cabecera, lineas con sus cantidades y placas, y las dos firmas en base64")
    @GetMapping("/{empresa}/{tipoDocumento}/{numero}")
    public ResponseEntity<ObjectResponse> getRequisition(
            @Parameter(hidden = true) @RequestHeader("Authorization") String bearerToken,
            @PathVariable @Pattern(regexp = PATRON_EMPRESA) String empresa,
            @PathVariable @Pattern(regexp = PATRON_TIPODOCU) String tipoDocumento,
            @PathVariable Long numero) {

        ObjectResponse response = new ObjectResponse();
        try {
            response = requisicionService.obtenerDetalle(empresa, tipoDocumento, numero);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error consultando la requisicion {}-{}-{}: {}", empresa, tipoDocumento, numero, e.getMessage(), e);
            return error(response, e);
        }
    }

    /**
     * Aprueba las lineas indicadas con su cantidad, que puede ser parcial. Las
     * que no vengan en el cuerpo no se tocan.
     */
    @Operation(summary = "Aprobar lineas",
               description = "Mueve a 'ap' las lineas indicadas con la cantidad aprobada. No toca el inventario")
    @PutMapping("/{empresa}/{tipoDocumento}/{numero}/aprobar")
    public ResponseEntity<ObjectResponse> approve(
            @Parameter(hidden = true) @RequestHeader("Authorization") String bearerToken,
            @PathVariable @Pattern(regexp = PATRON_EMPRESA) String empresa,
            @PathVariable @Pattern(regexp = PATRON_TIPODOCU) String tipoDocumento,
            @PathVariable Long numero,
            @Valid @RequestBody RequisicionLineasRequest request) {

        ObjectResponse response = new ObjectResponse();
        try {
            response = requisicionService.aprobar(empresa, tipoDocumento, numero, request, bearerToken);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error aprobando la requisicion {}-{}-{}: {}", empresa, tipoDocumento, numero, e.getMessage(), e);
            return error(response, e);
        }
    }

    /**
     * Entrega las lineas indicadas.
     *
     * Las placas son opcionales: sin ellas el paquete asigna las mas antiguas
     * del almacen, de modo que quien despacha recibe la lista resuelta. El
     * detalle las devuelve despues en ese mismo orden.
     */
    @Operation(summary = "Entregar lineas",
               description = "Mueve a 'en' las lineas indicadas, asigna las placas por FIFO si no vienen "
                             + "y abre las dos firmas de la requisicion")
    @PutMapping("/{empresa}/{tipoDocumento}/{numero}/entregar")
    public ResponseEntity<ObjectResponse> deliver(
            @Parameter(hidden = true) @RequestHeader("Authorization") String bearerToken,
            @PathVariable @Pattern(regexp = PATRON_EMPRESA) String empresa,
            @PathVariable @Pattern(regexp = PATRON_TIPODOCU) String tipoDocumento,
            @PathVariable Long numero,
            @Valid @RequestBody RequisicionLineasRequest request) {

        ObjectResponse response = new ObjectResponse();
        try {
            response = requisicionService.entregar(empresa, tipoDocumento, numero, request, bearerToken);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error entregando la requisicion {}-{}-{}: {}", empresa, tipoDocumento, numero, e.getMessage(), e);
            return error(response, e);
        }
    }

    @Operation(summary = "Anular al entregar",
               description = "Mueve a 'ae' las lineas indicadas. Solo aplica a lineas aprobadas sin entrega")
    @PutMapping("/{empresa}/{tipoDocumento}/{numero}/anular")
    public ResponseEntity<ObjectResponse> cancel(
            @Parameter(hidden = true) @RequestHeader("Authorization") String bearerToken,
            @PathVariable @Pattern(regexp = PATRON_EMPRESA) String empresa,
            @PathVariable @Pattern(regexp = PATRON_TIPODOCU) String tipoDocumento,
            @PathVariable Long numero,
            @Valid @RequestBody RequisicionAnularRequest request) {

        ObjectResponse response = new ObjectResponse();
        try {
            response = requisicionService.anular(empresa, tipoDocumento, numero, request, bearerToken);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error anulando la requisicion {}-{}-{}: {}", empresa, tipoDocumento, numero, e.getMessage(), e);
            return error(response, e);
        }
    }

    /**
     * No hay orden entre las dos firmas: cualquiera puede registrarse primero, y
     * ninguna mueve el estado de la requisicion.
     */
    @Operation(summary = "Registrar firma",
               description = "Guarda la firma de salida (SA) o la de recibe (RE), en cualquier orden, con su timestamp")
    @PutMapping("/{empresa}/{tipoDocumento}/{numero}/firmar")
    public ResponseEntity<ObjectResponse> sign(
            @Parameter(hidden = true) @RequestHeader("Authorization") String bearerToken,
            @PathVariable @Pattern(regexp = PATRON_EMPRESA) String empresa,
            @PathVariable @Pattern(regexp = PATRON_TIPODOCU) String tipoDocumento,
            @PathVariable Long numero,
            @Valid @RequestBody RequisicionFirmaRequest request) {

        ObjectResponse response = new ObjectResponse();
        try {
            response = requisicionService.registrarFirma(empresa, tipoDocumento, numero, request, bearerToken);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error registrando la firma de la requisicion {}-{}-{}: {}",
                    empresa, tipoDocumento, numero, e.getMessage(), e);
            return error(response, e);
        }
    }

    /**
     * Devuelve la firma como imagen, fuera de la envoltura JSON: sirve para
     * verificarla desde el navegador o para pintarla sin arrastrar el base64.
     *
     * La ruta exige el token como el resto del modulo, y un &lt;img src&gt; no manda
     * el header Authorization: el front debe traerla con fetch y construir un
     * object URL.
     */
    @Operation(summary = "Ver la firma", description = "Devuelve la imagen de la firma (SA salida, RE recibe)")
    @GetMapping("/{empresa}/{tipoDocumento}/{numero}/firmas/{tipo}")
    public ResponseEntity<byte[]> getSignature(
            @Parameter(hidden = true) @RequestHeader("Authorization") String bearerToken,
            @PathVariable @Pattern(regexp = PATRON_EMPRESA) String empresa,
            @PathVariable @Pattern(regexp = PATRON_TIPODOCU) String tipoDocumento,
            @PathVariable Long numero,
            @PathVariable String tipo) {

        try {
            byte[] firma = requisicionService.obtenerFirma(empresa, tipoDocumento, numero, tipo);

            if (firma == null || firma.length == 0) {
                return ResponseEntity.notFound().build();
            }

            return ResponseEntity.ok()
                    .contentType(FirmaUtil.mime(firma))
                    // Es una firma manuscrita: no debe quedar en cache del
                    // navegador ni de un proxy intermedio.
                    .header(HttpHeaders.CACHE_CONTROL, "no-store")
                    .body(firma);

        } catch (BussinessException e) {
            log.warn("Peticion invalida de firma para la requisicion {}-{}-{}: {}",
                    empresa, tipoDocumento, numero, e.getMessage());
            return ResponseEntity.badRequest().build();

        } catch (Exception e) {
            log.error("Error consultando la firma {} de la requisicion {}-{}-{}: {}",
                    tipo, empresa, tipoDocumento, numero, e.getMessage(), e);
            return ResponseEntity.status(500).build();
        }
    }

    /**
     * Cierra el ciclo. Exige las dos firmas y es AQUI donde se genera el
     * documento de salida: una cabecera en DOCUINVE y una linea por articulo en
     * MOVIINVE.
     *
     * El cuerpo es opcional y solo hace falta para recibir menos de lo
     * entregado. Lo que no venga se recibe completo.
     */
    @Operation(summary = "Registrar la salida",
               description = "PUNTO DE NO RETORNO. Exige las dos firmas y genera el documento de inventario en el ERP")
    @PutMapping("/{empresa}/{tipoDocumento}/{numero}/registrar")
    public ResponseEntity<ObjectResponse> register(
            @Parameter(hidden = true) @RequestHeader("Authorization") String bearerToken,
            @PathVariable @Pattern(regexp = PATRON_EMPRESA) String empresa,
            @PathVariable @Pattern(regexp = PATRON_TIPODOCU) String tipoDocumento,
            @PathVariable Long numero,
            @Valid @RequestBody(required = false) RequisicionRegistrarRequest request) {

        ObjectResponse response = new ObjectResponse();
        try {
            response = requisicionService.registrarSalida(empresa, tipoDocumento, numero, request, bearerToken);
            return ResponseEntity.ok(response);
        } catch (Exception e) {
            log.error("Error registrando la salida de la requisicion {}-{}-{}: {}",
                    empresa, tipoDocumento, numero, e.getMessage(), e);
            return error(response, e);
        }
    }

    /**
     * Respuesta de error comun.
     *
     * Usa OracleErrorUtil en vez del mensaje crudo porque los rechazos de este
     * modulo son de negocio y vienen del ERP: "no hay saldo suficiente",
     * "faltan placas por asignar". Ese texto es exactamente lo que el usuario
     * necesita leer, y sin extraerlo llegaria sepultado bajo el ORA-20008 y la
     * traza de Hibernate.
     */
    private ResponseEntity<ObjectResponse> error(ObjectResponse response, Exception e) {
        response.setCode(-1);
        response.setMsg(MSG_ERROR + OracleErrorUtil.mensajeNegocio(e));
        return ResponseEntity.status(500).body(response);
    }

    private ResponseEntity<ObjectListResponse> error(ObjectListResponse response, Exception e) {
        response.setCode(-1);
        response.setMsg(MSG_ERROR + OracleErrorUtil.mensajeNegocio(e));
        return ResponseEntity.status(500).body(response);
    }
}
