package com.finte.sigapp.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.finte.sigapp.exception.ErrorResponse;
import com.finte.sigapp.exception.catalog.ErrorCode;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Traduce el rechazo de Spring Security a la misma forma de respuesta que usa el
 * GlobalExceptionHandler. Sin esto, Spring responde 403 sin cuerpo para los tres
 * casos (sin token, token expirado, token invalido) y la app movil no puede
 * distinguir cuando renovar el token de cuando pedir credenciales otra vez.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationEntryPoint implements AuthenticationEntryPoint {

    private final ObjectMapper objectMapper;

    @Override
    public void commence(HttpServletRequest request, HttpServletResponse response,
            AuthenticationException authException) throws IOException {

        // El filtro deja aqui el diagnostico cuando llego un token pero no sirve.
        // Si no hay atributo es que la peticion llego sin header Authorization.
        ErrorCode errorCode = (ErrorCode) request.getAttribute(JwtAuthenticationFilter.ATTR_ERROR_AUTENTICACION);
        if (errorCode == null) {
            errorCode = ErrorCode.SIGAPP_408;
        }

        log.warn("Acceso no autenticado a {} {} - {}", request.getMethod(), request.getRequestURI(),
                errorCode.getCode());

        ErrorResponse body = ErrorResponse.builder()
                .success(false)
                .code(errorCode.getCode())
                .message(errorCode.getMessage())
                .build();

        response.setStatus(HttpStatus.UNAUTHORIZED.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        objectMapper.writeValue(response.getWriter(), body);
    }
}
