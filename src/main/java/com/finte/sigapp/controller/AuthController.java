package com.finte.sigapp.controller;

import com.finte.sigapp.dto.request.LoginContadorRequest;
import com.finte.sigapp.dto.request.LoginRequest;
import com.finte.sigapp.dto.response.AuthResponse;
import com.finte.sigapp.service.ContadorAuthService;
import com.finte.sigapp.service.LoginService;

import jakarta.validation.Valid;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@Slf4j
@RequestMapping("/api/v1/auth")
public class AuthController {

    private final LoginService loginService;
    private final ContadorAuthService contadorAuthService;

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest loginRequest) {
        return ResponseEntity.ok(loginService.login(loginRequest));
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