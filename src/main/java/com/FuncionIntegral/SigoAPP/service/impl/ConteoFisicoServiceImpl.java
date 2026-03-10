package com.FuncionIntegral.SigoAPP.service.impl;

import com.FuncionIntegral.SigoAPP.dto.request.ConteoFisicoRequest;
import com.FuncionIntegral.SigoAPP.dto.response.ConteoFisicoResponse;
import com.FuncionIntegral.SigoAPP.repository.ConteoFisicoRepository;
import com.FuncionIntegral.SigoAPP.service.ConteoFisicoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class ConteoFisicoServiceImpl implements ConteoFisicoService {

    private final ConteoFisicoRepository conteoFisicoRepository;

    @Override
    public ConteoFisicoResponse procesarConteoFisico(ConteoFisicoRequest request) {
        try {
            conteoFisicoRepository.llamarProcedimientoConteoFisico(
                    request.getEmpresa(),
                    request.getBodega(),
                    request.getBolo(),
                    request.getArticulo(),
                    request.getFecha(),
                    request.getVaex()
            );

            return ConteoFisicoResponse.builder()
                    .success(true)
                    .message("Conteo físico procesado exitosamente.")
                    .build();

        } catch (DataAccessException e) {
            log.error("Error al procesar el conteo físico en base de datos: {}", e.getMessage(), e);
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
