package com.finte.sigapp.controller;

import com.finte.sigapp.dto.request.LoginContadorRequest;
import com.finte.sigapp.dto.request.LoginRequest;
import com.finte.sigapp.dto.response.AuthResponse;
import com.finte.sigapp.repository.UsabadaRepository;
import com.finte.sigapp.security.JwtTokenProvider;
import com.finte.sigapp.service.ContadorAuthService;

import jakarta.validation.Valid;

import com.finte.sigapp.model.UsabadaModel;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class AuthController {

    private final UsabadaRepository usuarioRepository;
    private final JwtTokenProvider tokenProvider;
    private final PasswordEncoder passwordEncoder;
    private final ContadorAuthService contadorAuthService;

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody LoginRequest loginRequest) {
        // 1. Buscar usuario en Oracle
        UsabadaModel usuario = usuarioRepository
                .buscarPorUsername(loginRequest.getUsername())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        // 2. Validar contraseña (aquí comparas el hash de Oracle)
        if (!passwordEncoder.matches(loginRequest.getPassword(), usuario.getUsbdCont())) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Credenciales inválidas");
        }

        // 3. Generar JWT
        String token = tokenProvider.generarToken(usuario.getUsbdCodi());

        return ResponseEntity.ok(AuthResponse.builder()
                .token(token)
                .type("Bearer")
                .username(usuario.getUsbdCodi())
                .build());
    }

    @PostMapping("/login/contador")
    public ResponseEntity<AuthResponse> loginContador(@Valid @RequestBody LoginContadorRequest request) {
        return ResponseEntity.ok(contadorAuthService.loginContador(request));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refreshToken(
            @RequestHeader("Authorization") String bearerToken) {
        String token = bearerToken.replace("Bearer ", "").trim();
        return ResponseEntity.ok(contadorAuthService.refreshContador(token));
    }

}