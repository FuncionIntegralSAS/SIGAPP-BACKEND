package com.finte.sigapp.service.impl;

import com.finte.sigapp.dto.request.CierreConteoRequest;
import com.finte.sigapp.dto.request.ConteoFisicoRequest;
import com.finte.sigapp.dto.response.ConteoFisicoResponse;
import com.finte.sigapp.repository.ConteoFisicoRepository;
import com.finte.sigapp.repository.ContarboRepository;
import com.finte.sigapp.repository.FicofiarasRepository;
import com.finte.sigapp.service.ConteoFisicoService;
import com.finte.sigapp.security.JwtTokenProvider;
import com.finte.sigapp.exception.BussinessException;
import com.finte.sigapp.exception.UnauthorizedException;
import com.finte.sigapp.exception.catalog.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(rollbackFor = Exception.class)
public class ConteoFisicoServiceImpl implements ConteoFisicoService {

        private static final String ROL_ADMIN = "ADMIN";

        private final ConteoFisicoRepository conteoFisicoRepository;
        private final ContarboRepository contarboRepository;
        private final FicofiarasRepository ficofiarasRepository;
        private final JwtTokenProvider tokenProvider;

        @Override
        public ConteoFisicoResponse generarConteoFisico(ConteoFisicoRequest request) {
                // 1. Validar si la bodega ya se encuentra en conteo físico
                boolean bodegaEnConteo = conteoFisicoRepository.validarBodegaEnConteo(request.getEmpresa(),
                                request.getBodega(),
                                request.getBodegaLogica(),
                                request.getFecha());

                if (bodegaEnConteo) {
                        log.warn("La bodega {} ya se encuentra en conteo físico activo para el periodo {}.",
                                        request.getBodega(),
                                        request.getFecha());

                        throw new BussinessException(ErrorCode.SIGAPP_001,
                                        "No se puede realizar la solicitud de conteo: la bodega '" + request.getBodega()
                                                        +
                                                        "' ya se encuentra en proceso de conteo físico para el periodo '"
                                                        + request.getFecha()
                                                        + "'.");
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
                                .message("Conteo físico generado exitosamente para la bodega '" + request.getBodega()
                                                + "'.")
                                .build();
        }

        @Override
        public ConteoFisicoResponse cerrarConteo(String bearerToken, CierreConteoRequest request) {
                String token = tokenProvider.extraerToken(bearerToken);

                if (!tokenProvider.validarToken(token)) {
                        throw new UnauthorizedException(ErrorCode.SIGAPP_403);
                }

                // String rol = tokenProvider.getClaimFrowJWT(token, "rol");
                // if (!ROL_ADMIN.equals(rol)) {
                // log.error("Error al cerrar el conteo físico. El usuario no tiene permiso para
                // cerrar el conteo.");
                // throw new UnauthorizedException(ErrorCode.SIGAPP_405);
                // }
                log.debug("paso 1");
                int actualizadosFicofiaras = ficofiarasRepository.cerrarConteoPorBodega(request.getBodega(),
                                request.getEmpresa());
                int actualizados = contarboRepository.cerrarConteoPorBodega(request.getBodega(),
                                request.getEmpresa());

                if (actualizados > 0 || actualizadosFicofiaras > 0) {
                        return ConteoFisicoResponse.builder()
                                        .success(true)
                                        .message("Se ha cerrado el conteo para la bodega " + request.getBodega()
                                                        + " de la empresa "
                                                        + request.getEmpresa()
                                                        + " exitosamente.")
                                        .build();
                } else {
                        throw new BussinessException(ErrorCode.SIGAPP_002,
                                        "No se encontraron registros activos para la bodega " + request.getBodega()
                                                        + " de la empresa "
                                                        + request.getEmpresa() + ".");
                }
        }
}
