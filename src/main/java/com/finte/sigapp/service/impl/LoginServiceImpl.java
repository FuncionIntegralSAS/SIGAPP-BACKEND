package com.finte.sigapp.service.impl;

import org.springframework.stereotype.Service;

import com.finte.sigapp.dto.request.LoginRequest;
import com.finte.sigapp.dto.response.AuthResponse;
import com.finte.sigapp.dto.response.RoleformResponse;
import com.finte.sigapp.entity.RoleformEntity;
import com.finte.sigapp.exception.BussinessException;
import com.finte.sigapp.exception.catalog.ErrorCode;
import com.finte.sigapp.model.UsabadaModel;
import com.finte.sigapp.repository.RoleformRepository;
import com.finte.sigapp.repository.UsabadaRepository;
import com.finte.sigapp.security.JwtTokenProvider;
import com.finte.sigapp.service.LoginService;

import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import java.util.List;

@Slf4j
@Service
@AllArgsConstructor
public class LoginServiceImpl implements LoginService {

    private final UsabadaRepository usuarioRepository;
    private final RoleformRepository roleformRepository;
    private final JwtTokenProvider tokenProvider;
    private static final String ESTADO_ACTIVO = "ac";

    @Override
    public AuthResponse login(LoginRequest request) {

        log.info("Iniciando proceso de login para usuario: '{}'", request.getUsername());

        UsabadaModel usuario = usuarioRepository
                .buscarPorUsername(request.getUsername())
                .orElseThrow(() -> new BussinessException(
                        ErrorCode.SIGAPP_405,
                        "Usuario no existe"));

        userValidation(usuario, request.getPassword());

        String token = tokenProvider.generarToken(usuario.getUsbdcodi());

        log.info("Consultando permisos de formas para usuario: {}", usuario.getUsbdcodi());
        List<RoleformResponse> permisos = roleformRepository
                .findPermisosByUsuario(usuario.getUsbdcodi().trim()).stream()
                .map(this::mapToRoleFormResponse)
                .toList();

        log.info("Login exitoso para usuario: {}. Total permisos: {}", usuario.getUsbdcodi(), permisos.size());

        return AuthResponse.builder()
                .token("Bearer " + token)
                .type("Bearer")
                .username(request.getUsername())
                .expiresIn(100000)
                .permisos(permisos.stream().distinct().toList())
                .build();
    }

    private void userValidation(UsabadaModel user, String passFront) {
        Integer maxInfracciones = 2;

        if (!ESTADO_ACTIVO.equalsIgnoreCase(user.getUsbdesta())) {
            throw new BussinessException(ErrorCode.SIGAPP_406, "Usuario inactivo");
        }
        if (user.getUsbdinfa() > maxInfracciones) {
            throw new BussinessException(ErrorCode.SIGAPP_407, "Usuario bloqueado");
        }
        if (passFront == null || user.getUsbdcont() == null) {
            throw new BussinessException(ErrorCode.SIGAPP_401, "password es obligatorio");
        }

        if (!passFront.equals(user.getUsbdcont())) {
            log.info("Intento de login fallido: credenciales incorrectas");
            throw new BussinessException(ErrorCode.SIGAPP_401, "Credenciales inválidas");
        }
    }

    private RoleformResponse mapToRoleFormResponse(RoleformEntity entity) {
        return RoleformResponse.builder()
                .usuario(entity.getRoforole())
                .forma(entity.getRofoform())
                .tipoRol(entity.getRofotiro())
                .tipoForma(entity.getRofotifo())
                .producto(entity.getRofoprod())
                .build();
    }

}
