package com.finte.sigapp.service.impl;

import com.finte.sigapp.dto.request.AsignacionConteoRequest;
import com.finte.sigapp.dto.response.ConteoFisicoResponse;
import com.finte.sigapp.entity.ContarboEntity;
import com.finte.sigapp.entity.FicofiuscoEntity;
import com.finte.sigapp.repository.ContarboRepository;
import com.finte.sigapp.repository.FicofiarasRepository;
import com.finte.sigapp.repository.FicofiuscoRepository;
import com.finte.sigapp.service.FicofiarasService;
import com.finte.sigapp.service.FicofiuscoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class FicofiarasServiceImpl implements FicofiarasService {

    private final ContarboRepository contarboRepository;
    private final FicofiuscoRepository ficofiuscoRepository;
    private final FicofiarasRepository ficofiarasRepository;
    private final FicofiuscoService ficofiuscoService;

    @Override
    public ConteoFisicoResponse asignarArticulos(AsignacionConteoRequest request) {
        log.info("asignarArticulos init");
        List<FicofiuscoEntity> usuarios = ficofiuscoService.procesarUsuarios(request);
        //formateo fecha para query nativa de contarbo
        List<ContarboEntity> articulos = obtenerArticulos(request);
        //todo: implementar metodos en orden y probar, por ahora solo se coloca como va el flujo
//        List<List<ContarboEntity>> bloques = generarBloques(articulos,usuarios.size());
//        Collections.shuffle(bloques);
//        guardarAsignaciones(bloques,usuarios, request);
//        enviarCorreo(usuarios);

        return ConteoFisicoResponse.builder().success(true).message("OK").build();
    }

    private List<ContarboEntity> obtenerArticulos(AsignacionConteoRequest request){
        log.info("asignarArticulos formato fecha {}",request.getFechaConteo());
        String fecha = formatoFecha(request.getFechaConteo());
        log.info("asignarArticulos formateada fecha {}",fecha);

        List<ContarboEntity> articulos = contarboRepository.obtenerArticulosOrdenados(request.getEmpresa(),
                                                                                      request.getBodega(),
                                                                                      fecha);

        log.info("cantidad articulos {}",articulos.size());

        if (articulos.isEmpty()){
            throw new RuntimeException("No existen artículos en conteo activo para esta bodega");
        }

        return articulos;
    }
    private String formatoFecha(LocalDateTime input){
        return input.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
    }

}
