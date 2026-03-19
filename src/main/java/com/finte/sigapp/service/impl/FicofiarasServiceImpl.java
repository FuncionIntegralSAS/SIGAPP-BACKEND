package com.finte.sigapp.service.impl;

import com.finte.sigapp.dto.request.AsignacionConteoRequest;
import com.finte.sigapp.entity.ContarboEntity;
import com.finte.sigapp.entity.FicofiuscoEntity;
import com.finte.sigapp.repository.ContarboRepository;
import com.finte.sigapp.repository.FicofiarasRepository;
import com.finte.sigapp.repository.FicofiuscoRepository;
import com.finte.sigapp.service.FicofiarasService;
import com.finte.sigapp.service.FicofiuscoService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional
public class FicofiarasServiceImpl implements FicofiarasService {

    private final ContarboRepository contarboRepository;
    private final FicofiuscoRepository ficofiuscoRepository;
    private final FicofiarasRepository ficofiarasRepository;
    private final FicofiuscoServiceImpl ficofiuscoService;

    @Override
    public void asignarArticulos(AsignacionConteoRequest request) {
        //todo: implementar metodos en orden y probar, por ahora solo se coloca como va el flujo
        List<FicofiuscoEntity> usuarios = ficofiuscoService.procesarUsuarios(request);
//        List<ContarboEntity> articulos = obtenerArticulos(request);
//        List<List<ContarboEntity>> bloques = generarBloques(articulos,usuarios.size());
//        Collections.shuffle(bloques);
//        guardarAsignaciones(bloques,usuarios, request);
//        enviarCorreo(usuarios);
    }

}
