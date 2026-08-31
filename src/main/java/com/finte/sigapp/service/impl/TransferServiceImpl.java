package com.finte.sigapp.service.impl;

import com.finte.sigapp.dto.request.TransferProcessRequest;
import com.finte.sigapp.dto.request.TransferRequest;
import com.finte.sigapp.dto.request.TransferSignRequest;
import com.finte.sigapp.dto.response.ObjectListResponse;
import com.finte.sigapp.dto.response.ObjectResponse;
import com.finte.sigapp.dto.response.TransferCreatedResponse;
import com.finte.sigapp.dto.response.TransferDetailResponse;
import com.finte.sigapp.dto.response.TransferResponse;
import com.finte.sigapp.entity.MovitrasEntity;
import com.finte.sigapp.exception.BussinessException;
import com.finte.sigapp.exception.catalog.ErrorCode;
import com.finte.sigapp.model.MovitrasTraspasoModel;
import com.finte.sigapp.repository.MovitrasRepository;
import com.finte.sigapp.repository.TransferJpaRepository;
import com.finte.sigapp.repository.TransferRepository;
import com.finte.sigapp.security.JwtTokenProvider;
import com.finte.sigapp.service.TransferService;
import com.finte.sigapp.utils.FirmaUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class TransferServiceImpl implements TransferService {

    /** Convencion de la envoltura de respuesta. */
    private static final int CODE_OK = 0;
    private static final int CODE_ERROR = -1;

    private static final String ESTADO_PROCESADOS = "pr";
    private static final String ESTADO_APROBADO = "ap";
    private static final String ESTADO_RECHAZADO = "na";

    private final TransferRepository repository;
    private final TransferJpaRepository transferJpaRepository;
    private final MovitrasRepository movitrasRepository;
    private final JwtTokenProvider tokenProvider;
    private final FirmaUtil firmaUtil;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ObjectResponse crearSolicitud(TransferRequest request, String bearerToken) {

        String usuarioCrea = obtenerUsuario(bearerToken);

        if (request.getPersonaFuente().equals(request.getPersonaDestino())) {
            return new ObjectResponse(CODE_ERROR,
                    "La persona fuente y la persona destino no pueden ser la misma");
        }

        // El paquete crea la requisicion en REQUSUMI y despues el traspaso, todo
        // dentro de la misma transaccion.
        TransferCreatedResponse creado = repository.crearSolicitud(
                request.getEmpresa(),
                request.getPersonaFuente(),
                request.getPersonaDestino(),
                request.getElemento(),
                request.getPlaca(),
                request.getObservacion(),
                request.getTipoMovimiento(),
                usuarioCrea);

        if (creado == null) {
            return new ObjectResponse(CODE_ERROR,
                    "El traspaso se proceso pero el procedimiento no retorno ID");
        }

        log.info("Traspaso {} creado sobre el documento {}-{}-{}",
                creado.getId(), creado.getEmpresaDocumento(),
                creado.getTipoDocumento(), creado.getNumeroDocumento());

        return new ObjectResponse(CODE_OK, "El traspaso fue creado correctamente", creado);
    }

    @Override
    public ObjectListResponse listarSolicitudes(String empresa, String bodega, String estado) {

        List<String> estadosDb;

        if (ESTADO_PROCESADOS.equalsIgnoreCase(estado)) {
            // "Procesados" agrupa todo lo que ya salio de la bandeja de pendientes.
            estadosDb = Arrays.asList(ESTADO_APROBADO, ESTADO_RECHAZADO, "af", "ad", "re");
        } else {
            estadosDb = Collections.singletonList(estado);
        }

        log.info("Listando traspasos en estados {} - empresa {}, bodega {}", estadosDb, empresa, bodega);

        // ObjectListResponse expone List<Object>, y List<TransferResponse> no es
        // asignable a esa lista: se copia a una ArrayList<Object>.
        List<Object> traspasos = new ArrayList<>(
                transferJpaRepository.buscarPorEstados(estadosDb, empresa, bodega).stream()
                        .map(this::mapToResponse)
                        .toList());

        return new ObjectListResponse(CODE_OK, "Consulta exitosa", traspasos);
    }

    @Override
    public ObjectResponse obtenerDetalle(Long idTramite) {

        Optional<MovitrasEntity> traspaso = movitrasRepository.findById(idTramite);

        if (traspaso.isEmpty()) {
            return new ObjectResponse(CODE_ERROR, "No existe el traspaso " + idTramite);
        }

        return new ObjectResponse(CODE_OK, "Consulta exitosa", mapToDetail(traspaso.get()));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ObjectResponse procesarTraspaso(Long idTramite, 
                                           TransferProcessRequest request, 
                                           String bearerToken) {

        String usuarioActualiza = obtenerUsuario(bearerToken);

        if (ESTADO_RECHAZADO.equals(request.getEstado()) && !StringUtils.hasText(request.getObservacion())) {
            return new ObjectResponse(CODE_ERROR, "Debe incluir una observacion al rechazar el traspaso");
        }

        repository.aprobarRechazar(
                idTramite,
                request.getEstado(),
                request.getObservacion(),
                usuarioActualiza);

        return new ObjectResponse(CODE_OK, "El trámite fue procesado correctamente");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ObjectResponse registrarFirma(Long idTramite, TransferSignRequest request, String bearerToken) {

        byte[] firma;
        try {
            // El base64 llega del canvas del front: puede traer prefijo data URL y no
            // hay garantia de que sea una imagen. Se normaliza antes de tocar la BD.
            firma = firmaUtil.decodificar(request.getFirma());
        } catch (BussinessException e) {
            log.warn("Firma rechazada para el traspaso {}: {}", idTramite, e.getMessage());
            return new ObjectResponse(CODE_ERROR, e.getMessage());
        }

        // El estado del tramite y el orden de las firmas los valida el paquete.
        repository.registrarFirma(
                idTramite,
                request.getTipoFirma(),
                firma,
                obtenerUsuario(bearerToken));

        return new ObjectResponse(CODE_OK, "La firma fue registrada correctamente");
    }

    @Override
    public byte[] obtenerFirma(Long idTramite, String tipoFirma) {

        String tipo = tipoFirma == null ? "" : tipoFirma.trim().toUpperCase();

        if (!TransferRepository.FIRMA_FUENTE.equals(tipo) && !TransferRepository.FIRMA_DESTINO.equals(tipo)) {
            throw new BussinessException(ErrorCode.SIGAPP_400,
                    "El tipo de firma solo puede ser 'FU' (fuente) o 'DE' (destino)");
        }

        Optional<MovitrasEntity> traspaso = movitrasRepository.findById(idTramite);

        if (traspaso.isEmpty()) {
            log.warn("Se pidio la firma {} de un traspaso inexistente: {}", tipo, idTramite);
            return null;
        }

        MovitrasEntity entity = traspaso.get();

        return TransferRepository.FIRMA_FUENTE.equals(tipo) ? entity.getMotrfifu() : entity.getMotrfide();
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ObjectResponse recibir(Long idTramite, String bearerToken) {

        repository.recibir(idTramite, obtenerUsuario(bearerToken));

        return new ObjectResponse(CODE_OK, "La recepción fue registrada correctamente");
    }

    /**
     * Usuario que queda registrado en FI_MOVITRAS (MOTRUSCR / MOTRUSAP). Es el
     * subject del token, que para el login administrativo es usbdcodi.
     */
    private String obtenerUsuario(String bearerToken) {
        String token = tokenProvider.extraerToken(bearerToken);
        return tokenProvider.getUsernameFromJWT(token);
    }

    private TransferResponse mapToResponse(MovitrasTraspasoModel model) {
        return TransferResponse.builder()
                .id(model.getMotridmt())
                .estado(model.getMotresta())
                .empresaDocumento(model.getMotrdrem())
                .tipoDocumento(model.getMotrdrtd())
                .numeroDocumento(model.getMotrdrnd())
                .elemento(model.getMotrarti())
                .placaElemento(model.getMotrplac())
                .nombreElemento(model.getNombElem())
                .personaFuente(model.getPersFuen())
                .personaDestino(model.getPersDest())
                .bodegaFuente(model.getBodeFuen())
                .bodegaDestino(model.getBodeDest())
                .observacion(model.getMotrobse())
                .motivoRechazo(model.getMotrmore())
                .fechaCreacion(model.getMotrfecr())
                .usuarioCreacion(model.getMotruscr())
                .fechaAprobacion(model.getMotrfeap())
                .usuarioAprobacion(model.getMotrusap())
                .fechaAceptacionFuente(model.getMotrfafu())
                .fechaAceptacionDestino(model.getMotrfade())
                .fechaRecibe(model.getMotrfere())
                .build();
    }

    /**
     * Las firmas se guardan como bytes, pero el detalle las expone en base64: es lo
     * que el front puede meter directo en un {@code <img src>} sin una peticion
     * extra. Para servirlas como imagen esta {@link #obtenerFirma(Long, String)}.
     */
    private String aBase64(byte[] firma) {
        return firma == null || firma.length == 0 ? null : Base64.getEncoder().encodeToString(firma);
    }

    private TransferDetailResponse mapToDetail(MovitrasEntity entity) {
        return TransferDetailResponse.builder()
                .id(entity.getMotridmt())
                .estado(entity.getMotresta())
                .empresaDocumento(entity.getMotrdrem())
                .tipoDocumento(entity.getMotrdrtd())
                .numeroDocumento(entity.getMotrdrnd())
                .elemento(entity.getMotrarti())
                .placaElemento(entity.getMotrplac())
                .personaFuente(entity.getMotrpefu())
                .personaDestino(entity.getMotrpede())
                .observacion(entity.getMotrobse())
                .motivoRechazo(entity.getMotrmore())
                .fechaCreacion(entity.getMotrfecr())
                .usuarioCreacion(entity.getMotruscr())
                .fechaAprobacion(entity.getMotrfeap())
                .usuarioAprobacion(entity.getMotrusap())
                .fechaAceptacionFuente(entity.getMotrfafu())
                .fechaAceptacionDestino(entity.getMotrfade())
                .fechaRecibe(entity.getMotrfere())
                .firmaFuente(aBase64(entity.getMotrfifu()))
                .firmaDestino(aBase64(entity.getMotrfide()))
                .build();
    }
}
