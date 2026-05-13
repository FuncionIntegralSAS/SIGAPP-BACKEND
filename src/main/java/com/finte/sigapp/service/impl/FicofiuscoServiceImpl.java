package com.finte.sigapp.service.impl;

import com.finte.sigapp.dto.UsuarioConteoDTO;
import com.finte.sigapp.dto.request.AsignacionConteoRequest;
import com.finte.sigapp.entity.FicofiuscoEntity;
import com.finte.sigapp.entity.PersonalEntity;
import com.finte.sigapp.repository.FicofiuscoRepository;
import com.finte.sigapp.repository.PersonalJpaRepository;
import com.finte.sigapp.service.FicofiuscoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.ThreadLocalRandom;

@Service
@RequiredArgsConstructor
@Slf4j
public class FicofiuscoServiceImpl implements FicofiuscoService {

    private final FicofiuscoRepository ficofiuscoRepository;
    private final PersonalJpaRepository personalJpaRepository;

    public List<FicofiuscoEntity> procesarUsuarios(AsignacionConteoRequest request){
        return request.getUsuarios().stream()
//                .map(this::validarUsuarioPersona) //todo: realizar método a parte
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
        log.info("mapeando usuario");
        String codigoTemporal = generarCodigoTemporal();
        LocalDateTime fechaCreacion = LocalDateTime.now();

        log.info("codigoTemporal {} fecha {} documento {}",
                codigoTemporal, fechaCreacion, dto.getDocumento());

        return ficofiuscoRepository.findByUSCODOCU(dto.getDocumento())
                .map(usuario -> actualizarUsuarioExistente(
                        usuario,
                        codigoTemporal,
                        fechaCreacion))
                .orElseGet(() -> crearNuevoUsuario(
                        dto,
                        codigoTemporal,
                        fechaCreacion
                ));
    }
    private String generarCodigoTemporal(){
        log.info("generando codigo temporal");
        return String.valueOf(ThreadLocalRandom.current().nextInt(100000,999999));
    }

    private FicofiuscoEntity actualizarUsuarioExistente(FicofiuscoEntity usuario,
                                                        String codigotemporal,
                                                        LocalDateTime fechaCreacion){
        log.info("Actualizando usuario existente");
        usuario.setUSCOCODI(codigotemporal);
        usuario.setUSCOFECR(fechaCreacion);
        usuario.setUSCOESTA("ac");

        return usuario;
    }

    private FicofiuscoEntity crearNuevoUsuario(UsuarioConteoDTO dto,
                                               String codigoTemporal,
                                               LocalDateTime fechaCreacion){
        FicofiuscoEntity entity = new FicofiuscoEntity();
        entity.setUSCOIDUS(obtenerSiguienteIdUsuario());
        entity.setUSCODOCU(dto.getDocumento());
        entity.setUSCONOMB(dto.getNombre());
        entity.setUSCOEMAI(dto.getEmail());
        entity.setUSCOCODI(codigoTemporal);
        entity.setUSCOESTA("ac");
        entity.setUSCOFECR(fechaCreacion);

        return entity;
    }
    private Long obtenerSiguienteIdUsuario(){
        return ficofiuscoRepository.obtenerSiguienteId();
    }
}
