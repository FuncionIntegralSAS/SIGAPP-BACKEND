package com.finte.sigapp.service.impl;

import com.finte.sigapp.dto.request.TransferProcessRequest;
import com.finte.sigapp.dto.request.TransferRequest;
import com.finte.sigapp.dto.response.TransferResponse;
import com.finte.sigapp.model.SigappTraspasoModel;
import com.finte.sigapp.repository.TransferRepository;
import com.finte.sigapp.service.TransferService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class TransferServiceImpl implements TransferService {

    private final TransferRepository repository;

    @Override
    public String crearSolicitud(TransferRequest request, String usuarioCrea) {

        // Llamada directa usando los datos del Front
        String idGenerado = repository.llamarProcedimientoCrear(
                request.getEmpresa(),
                request.getTipoMov(),
                request.getArtiId(),
                request.getPlaca(),
                request.getPersonaDestino(),
                request.getObservacion(),
                usuarioCrea
        );

        if (idGenerado == null) {
            throw new RuntimeException("La solicitud se procesó pero no retornó ID.");
        }

        return idGenerado;
    }

    @Override
    public List<TransferResponse> listarSolicitudes(String bodegaId, String estadoFront) {

        List<String> estadosDb;

        if ("pr".equalsIgnoreCase(estadoFront)) {
            // "Procesados" implica Aprobados (ap) y Rechazados/No Aprobados (na)
            estadosDb = Arrays.asList("ap", "na");
        } else {
            // "Pendientes" (pe) u otros estados simples
            estadosDb = Collections.singletonList(estadoFront);
        }

        return repository.listarPorBodegaYEstados(bodegaId, estadosDb).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    private TransferResponse mapToResponse(SigappTraspasoModel model) {
        return TransferResponse.builder()
                .id(model.getTrasId())
                .articuloID(model.getTrasArti())

                .personaFuente(model.getPersonaFuenteDesc())
                .personaDestino(model.getPersonaDestinoDesc())

                .bodegaFuente(model.getBodegaFuenteDesc())
                .bodegaDestino(model.getBodegaDestinoDesc())

                .descripcionTramite(model.getTrasObserv())
                .fechaCreacion(model.getTrasFechCrea())
                .estado(model.getTrasEstado())
                .motivoRechazo(model.getTrasRech())
                .build();
    }

    @Override
    public void procesarTraspaso(Long idTramite, TransferProcessRequest request, String usuarioActualiza) {

        if ("na".equals(request.getEstado()) &&
                (request.getObservacion() == null || request.getObservacion().trim().isEmpty())) {
            throw new IllegalArgumentException("Debe incluir una observación al rechazar el trámite.");
        }

        repository.callApproveRejectProcedure(
                idTramite,
                request.getEstado(),
                request.getObservacion(),
                usuarioActualiza
        );
    }
}