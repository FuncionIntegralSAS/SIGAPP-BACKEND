package com.finte.sigapp.security;

import com.finte.sigapp.entity.FicofiuscoEntity;
import com.finte.sigapp.exception.UnauthorizedException;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.extern.slf4j.Slf4j;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Date;

@Component
@Slf4j
public class JwtTokenProvider {

    @Value("${app.jwt.secret}")
    private String secret;

    @Value("${app.jwt.access-token-expiration}")
    private long accessTokenExpiration;

    @Value("${app.jwt.refresh-token-expiration}")
    private long refreshTokenExpiration;

    public String generarToken(String username) {
        return Jwts.builder()
                .subject(username)
                // .claim("rol", "ADMIN")
                // .claim("tipo", "access")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + accessTokenExpiration))
                .signWith(getSigningKey())
                .compact();
    }

    private SecretKey getSigningKey() {
        return Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    public String generarTokenContador(FicofiuscoEntity user) {
        return Jwts.builder()
                .subject(user.getUSCODOCU())
                .claim("idUsuario", user.getUSCOIDUS())
                .claim("nombre", user.getUSCONOMB())
                .claim("estado", user.getUSCOESTA())
                .claim("rol", "CONTADOR")
                .claim("tipo", "access")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + accessTokenExpiration))
                .signWith(getSigningKey())
                .compact();
    }

    public String generarRefreshTokenContador(FicofiuscoEntity user) {
        return Jwts.builder()
                .subject(user.getUSCODOCU())
                .claim("rol", "CONTADOR")
                .claim("tipo", "refresh")
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + refreshTokenExpiration))
                .signWith(getSigningKey())
                .compact();
    }

    /* Utilidades */
    private Claims extraerClaims(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }

    public String getUsernameFromJWT(String token) {
        return extraerClaims(token).getSubject();
    }

    public Long extraerIdUsuario(String token) {
        Object id = extraerClaims(token).get("idUsuario");
        if (id == null) {
            throw new UnauthorizedException("Token sin claim idUsuario");
        }
        if (id instanceof Integer)
            return ((Integer) id).longValue();
        if (id instanceof Long)
            return (Long) id;

        throw new UnauthorizedException("Claim idUsuario no soportado");
    }

    public String getClaimFrowJWT(String token, String claim) {
        return extraerClaims(token).get(claim, String.class);
    }

    public boolean isRefreshToken(String token) {
        return "refresh".equals(getClaimFrowJWT(token, "tipo"));
    }

    public boolean validarToken(String token) {
        try {
            extraerClaims(token);
            return true;
        } catch (ExpiredJwtException e) {
            log.warn("Token expirado: {}", e.getMessage());
        } catch (JwtException e) {
            log.warn("Token invalido {}", e.getMessage());
        } catch (IllegalArgumentException e) {
            log.warn("Token invalido {}", e.getMessage());
        }
        return false;
    }

    public String extraerToken(String bearerToken) throws Exception {

        if (bearerToken == null || bearerToken.isBlank())
            throw new UnauthorizedException("Token null o en blanco");
        String decode = URLDecoder.decode(bearerToken, StandardCharsets.UTF_8);
        if (!decode.startsWith("Bearer ")) {
            throw new UnauthorizedException("Formato de token invalido");
        }
        return decode.substring(7).trim();
    }
}