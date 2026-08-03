package com.finte.sigapp.security;

import com.finte.sigapp.exception.catalog.ErrorCode;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.List;

@Component
@RequiredArgsConstructor
@Slf4j
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    /**
     * Atributo donde el filtro deja el motivo del rechazo para que
     * JwtAuthenticationEntryPoint pueda responder con el ErrorCode correcto.
     */
    public static final String ATTR_ERROR_AUTENTICACION = "sigapp.error.autenticacion";

    private final JwtTokenProvider tokenProvider;

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String token = getJwtFromRequest(request);

        if (StringUtils.hasText(token)) {
            ErrorCode error = tokenProvider.diagnosticarToken(token);

            // Un refresh token esta firmado y vigente, pero no habilita las APIs:
            // dura 24h y solo sirve para renovar en /api/v1/auth/refresh, que lee
            // el header por su cuenta y no depende de este filtro.
            if (error == null && !tokenProvider.isAccessToken(token)) {
                log.warn("Token de tipo no valido para autenticar en {}", request.getRequestURI());
                error = ErrorCode.SIGAPP_403;
            }

            if (error == null) {
                SecurityContextHolder.getContext().setAuthentication(construirAutenticacion(token));
            } else {
                // No se corta la cadena: los endpoints publicos deben seguir respondiendo
                // aunque llegue un token vencido. Si la ruta si requiere autenticacion,
                // el entry point leera este atributo.
                request.setAttribute(ATTR_ERROR_AUTENTICACION, error);
            }
        }

        filterChain.doFilter(request, response);
    }

    private UsernamePasswordAuthenticationToken construirAutenticacion(String token) {
        String username = tokenProvider.getUsernameFromJWT(token);
        String rol = tokenProvider.getClaimFrowJWT(token, "rol");

        List<GrantedAuthority> authorities = StringUtils.hasText(rol)
                ? List.of(new SimpleGrantedAuthority("ROLE_" + rol))
                : Collections.emptyList();

        return new UsernamePasswordAuthenticationToken(username, null, authorities);
    }

    private String getJwtFromRequest(HttpServletRequest request) {
        String bearerToken = request.getHeader("Authorization");
        if (StringUtils.hasText(bearerToken) && bearerToken.startsWith("Bearer ")) {
            return bearerToken.substring(7);
        }
        return null;
    }
}
