package com.finte.sigapp.service.impl;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.finte.sigapp.dto.request.LoginContadorRequest;
import com.finte.sigapp.dto.response.AuthResponse;
import com.finte.sigapp.entity.FicofiuscoEntity;
import com.finte.sigapp.exception.UnauthorizedException;
import com.finte.sigapp.exception.catalog.ErrorCode;
import com.finte.sigapp.repository.FicofiuscoRepository;
import com.finte.sigapp.security.JwtTokenProvider;
import com.finte.sigapp.service.ContadorAuthService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class ContadorAuthServiceImpl implements ContadorAuthService {

    private final FicofiuscoRepository ficofiuscoRepository;
    private final JwtTokenProvider tokenProvider;

    @Value("${app.jwt.access-token-expiration}")
    private long accessTokenExpiration;

    @Override
    public AuthResponse loginContador(LoginContadorRequest request) {
        log.info("Iniciando login para el documento: {}", request.getDocumento());

        FicofiuscoEntity usuario = ficofiuscoRepository.findByUSCODOCU(request.getDocumento())
                // .orElseThrow(() -> new UnauthorizedException("Usuario no encontrado"));
                .orElseThrow(() -> new UnauthorizedException(ErrorCode.SIGAPP_007));

        if ("!ac".equalsIgnoreCase(usuario.getUSCOESTA())) {
            throw new UnauthorizedException(ErrorCode.SIGAPP_008);
        }

        if (!usuario.getUSCOCODI().equals(request.getCodigoTemporal())) {
            throw new UnauthorizedException(ErrorCode.SIGAPP_009);
        }

        String accessToken = tokenProvider.generarTokenContador(usuario);
        String refreshToken = tokenProvider.generarRefreshTokenContador(usuario);

        log.info("Login contador exitoso - documento {}", usuario.getUSCODOCU());

        return AuthResponse.builder()
                .token(accessToken)
                .refreshToken(refreshToken)
                .type("Bearer")
                .username(usuario.getUSCONOMB())
                .expiresIn(accessTokenExpiration / 1000)
                .build();
    }

    @Override
    public AuthResponse refreshContador(String refreshToken) {
        log.info("Refresh token contador");

        if (!tokenProvider.validarToken(refreshToken)) {
            throw new UnauthorizedException(ErrorCode.SIGAPP_402);
        }
        if (!tokenProvider.isRefreshToken(refreshToken)) {
            throw new UnauthorizedException(ErrorCode.SIGAPP_403);
        }

        String documento = tokenProvider.getUsernameFromJWT(refreshToken);

        FicofiuscoEntity usuario = ficofiuscoRepository
                .findByUSCODOCU(documento)
                .orElseThrow(() -> new UnauthorizedException(ErrorCode.SIGAPP_007));

        return AuthResponse.builder()
                .token(tokenProvider.generarTokenContador(usuario))
                .refreshToken(refreshToken)
                .type("Bearer")
                .username(usuario.getUSCONOMB())
                .expiresIn(accessTokenExpiration / 1000)
                .build();
    }

}
