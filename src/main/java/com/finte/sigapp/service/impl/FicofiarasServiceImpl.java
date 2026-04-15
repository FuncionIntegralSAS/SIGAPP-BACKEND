package com.finte.sigapp.service.impl;

import com.finte.sigapp.dto.request.AsignacionConteoRequest;
import com.finte.sigapp.dto.response.ConteoFisicoResponse;
import com.finte.sigapp.entity.ContarboEntity;
import com.finte.sigapp.entity.FicofiarasEntity;
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
import java.util.*;

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
        Map<FicofiuscoEntity,List<ContarboEntity>> bloques = generarBloques(usuarios,articulos);
        persistirAsignaciones(bloques,request);
//        enviarCorreo(usuarios);

        return ConteoFisicoResponse.builder().success(true).message("OK").build();
    }

    private List<ContarboEntity> obtenerArticulos(AsignacionConteoRequest request){
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

    private Map<FicofiuscoEntity, List<ContarboEntity>> generarBloques(List<FicofiuscoEntity> usuarios,
                                                                       List<ContarboEntity> articulos){
        List<FicofiuscoEntity> usuariosRandom = new ArrayList<>(usuarios);
        Collections.shuffle(usuariosRandom);

        Map<FicofiuscoEntity,List<ContarboEntity>> bloques = new LinkedHashMap<>();

        int totalArticulos = articulos.size();
        int totalUsuarios = usuarios.size();
        int base = totalArticulos/totalUsuarios;
        int residuo = totalArticulos % totalUsuarios;

        int desde = 0;

        for (int i = 0; i < usuariosRandom.size(); i++){
            int cantidad = base + (i< residuo ? 1: 0);
            int hasta = desde + cantidad;

            List<ContarboEntity> bloque  = articulos.subList(desde,hasta);
            bloques.put(usuariosRandom.get(i),bloque);

            desde = hasta;
        }
        return bloques;
    }

    private void persistirAsignaciones(Map<FicofiuscoEntity,List<ContarboEntity>> bloques,
                                       AsignacionConteoRequest request){
        Long idAsignacion = ficofiarasRepository.obtenerIdConteo();

        List<FicofiarasEntity> asignaciones =  new ArrayList<>();

        for(Map.Entry<FicofiuscoEntity, List<ContarboEntity>> entry : bloques.entrySet()){

            FicofiuscoEntity usuario = entry.getKey();

            for (ContarboEntity articulo : entry.getValue()) {

                FicofiarasEntity asignacion = new FicofiarasEntity();

                asignacion.setARASIDAS(ficofiarasRepository.obtenerSiguienteId());
                asignacion.setARASIDCO(idAsignacion);
                asignacion.setARASIDBO(request.getBodega());
                asignacion.setARASIDAR(articulo.getCoabArti());
                asignacion.setARASIDUS(usuario.getUSCOIDUS());
                asignacion.setARASNUCO(1);
                asignacion.setARASCOQR(articulo.getCoabPlac());
                asignacion.setARASCANT(null);
                asignacion.setARASFECO(LocalDateTime.now());
                asignacion.setARASESTA("pe");
                asignacion.setARASSINC("N");

                asignaciones.add(asignacion);
            }
        }
        ficofiarasRepository.saveAll(asignaciones);
    }
}
