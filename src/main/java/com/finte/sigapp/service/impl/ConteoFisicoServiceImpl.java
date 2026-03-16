package com.finte.sigapp.service.impl;

import com.finte.sigapp.dto.request.ConteoFisicoRequest;
import com.finte.sigapp.dto.response.ConteoFisicoResponse;
import com.finte.sigapp.repository.ConteoFisicoRepository;
import com.finte.sigapp.service.ConteoFisicoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(rollbackFor =  Exception.class)
public class ConteoFisicoServiceImpl implements ConteoFisicoService {

    private final ConteoFisicoRepository conteoFisicoRepository;

    @Override
    public ConteoFisicoResponse generarConteoFisico(ConteoFisicoRequest request) {
        try {
            // 1. Validar si la bodega ya se encuentra en conteo físico
            boolean bodegaEnConteo = conteoFisicoRepository.validarBodegaEnConteo(request.getEmpresa(),
                                                                                  request.getBodega(),
                                                                                  request.getBodegaLogica(),
                                                                                  request.getFecha());

            if (bodegaEnConteo) {
                log.warn("La bodega {} ya se encuentra en conteo físico activo para el periodo {}.",request.getBodega()
                        ,request.getFecha());

                return ConteoFisicoResponse.builder()
                        .success(false)
                        .message("No se puede realizar la solicitud de conteo: la bodega '"+ request.getBodega()+
                                "' ya se encuentra en proceso de conteo físico para el periodo '"+ request.getFecha() + "'.")
                        .build();
            }

            // 2. Si no está en conteo, procesar el conteo físico
            log.info("Bodega {} disponible. generando conteo físico...", request.getBodega());

            conteoFisicoRepository.generarConteoFisico(
                    request.getEmpresa(),
                    request.getBodega(),
                    request.getBodegaLogica(),
                    request.getArticulo(),
                    request.getFecha(),
                    request.getVerificarExistencia());


            return ConteoFisicoResponse.builder()
                    .success(true)
                    .message("Conteo físico generado exitosamente para la bodega '"+ request.getBodega() + "'.")
                    .build();

        } catch (DataAccessException e) {
            log.error("Error de base de datos al procesar el conteo físico: {}", e.getMessage(), e);
            return ConteoFisicoResponse.builder()
                    .success(false)
                    .message("Error al procesar en base de datos: " + e.getMostSpecificCause().getMessage())
                    .build();
        } catch (Exception e) {
            log.error("Error general al procesar el conteo físico: {}", e.getMessage(), e);
            return ConteoFisicoResponse.builder()
                    .success(false)
                    .message("Error inesperado al procesar el conteo físico: " + e.getMessage())
                    .build();
        }
    }
}
