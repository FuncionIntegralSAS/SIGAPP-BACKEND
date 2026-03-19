package com.finte.sigapp.service.impl;

import com.finte.sigapp.dto.UsuarioConteoDTO;
import com.finte.sigapp.dto.request.AsignacionConteoRequest;
import com.finte.sigapp.entity.FicofiuscoEntity;
import com.finte.sigapp.entity.PersonalEntity;
import com.finte.sigapp.repository.FicofiuscoRepository;
import com.finte.sigapp.repository.PersonalJpaRepository;
import com.finte.sigapp.service.FicofiuscoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
public class FicofiuscoServiceImpl implements FicofiuscoService {

    private final FicofiuscoRepository ficofiuscoRepository;
    private final PersonalJpaRepository personalJpaRepository;

    public List<FicofiuscoEntity> procesarUsuarios(AsignacionConteoRequest request){
        return request.getUsuarios().stream()
                .map(this::validarUsuarioPersona) //todo: probar y validar si dejó aquí la validación
                .map(this::mapearOActualizarUsuario)
                .map(ficofiuscoRepository::save)
                .toList();
    }
   private UsuarioConteoDTO validarUsuarioPersona(UsuarioConteoDTO dto){

        PersonalEntity persona = personalJpaRepository.findByPersiden(dto.getDocumento())
                .orElseThrow(() -> new RuntimeException("Persona no encontrada"));

        dto.setNombre(persona.getPersnomb());
        dto.setEmail(persona.getPerscoel());
        return dto;
    }
    private FicofiuscoEntity mapearOActualizarUsuario(UsuarioConteoDTO dto){
        String codigoTemporal = generarCodigoTemporal();
        LocalDateTime ahora = LocalDateTime.now();

        return ficofiuscoRepository.findByUSCODOCU(dto.getDocumento())
                .map(usuario -> {
                    usuario.setUSCOCODI(codigoTemporal);
                    usuario.setUSCOESTA("ac");
                    usuario.setUSCOFECR(ahora);
                    return usuario;
                })
                .orElseGet(() -> {
                    FicofiuscoEntity nuevo = new FicofiuscoEntity();
                    nuevo.setUSCOIDUS(obtenerSiguienteIdUsuario());
                    nuevo.setUSCODOCU(dto.getDocumento());
                    nuevo.setUSCONOMB(dto.getNombre());
                    nuevo.setUSCOEMAI(dto.getEmail());
                    nuevo.setUSCOCODI(codigoTemporal);
                    nuevo.setUSCOESTA("ac");
                    nuevo.setUSCOFECR(ahora);
                    return nuevo;
                });
    }
    private String generarCodigoTemporal(){
        return String.valueOf(ThreadLocalRandom.current().nextInt(100000,999999));
    }
    private Long obtenerSiguienteIdUsuario(){
        return ficofiuscoRepository.obtenerSiguienteId();
    }
}
