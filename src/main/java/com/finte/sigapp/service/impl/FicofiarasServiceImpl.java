package com.finte.sigapp.service.impl;

import com.finte.sigapp.dto.request.AsignacionConteoRequest;
import com.finte.sigapp.dto.response.ConteoFisicoResponse;
import com.finte.sigapp.entity.ContarboEntity;
import com.finte.sigapp.entity.FicofiarasEntity;
import com.finte.sigapp.entity.FicofiuscoEntity;
import com.finte.sigapp.repository.ContarboRepository;
import com.finte.sigapp.repository.FicofiarasRepository;
import com.finte.sigapp.service.EmailService;
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

    private static final String ESTADO_PENDIENTE = "pe";
    private static final String SIN_CONTAR = "N";
    private static final int NUMERO_CONTEO_INICIAL = 1;

    private final ContarboRepository contarboRepository;
    private final FicofiarasRepository ficofiarasRepository;
    private final FicofiuscoService ficofiuscoService;
    private final EmailService emailService;

    @Override
    public ConteoFisicoResponse asignarArticulos(AsignacionConteoRequest request) {
        log.info("asignarArticulos init");
        List<FicofiuscoEntity> usuarios = ficofiuscoService.procesarUsuarios(request);
        // formateo fecha para query nativa de contarbo
        List<ContarboEntity> articulos = obtenerArticulos(request);
        Map<FicofiuscoEntity, List<ContarboEntity>> bloques = generarBloques(usuarios, articulos);
        persistirAsignaciones(bloques, request);
        // todo: implementar metodos en orden y probar, por ahora solo se coloca como va
        // el flujo
        emailService.enviarCodigoAcceso(usuarios);

        return ConteoFisicoResponse.builder()
                .success(true)
                .message("OK")
                .build();
    }

    private List<ContarboEntity> obtenerArticulos(AsignacionConteoRequest request) {
        String fecha = formatoFechaDDmmYYYY(request.getFechaConteo());

        log.info("asignarArticulos formateada fecha {}", fecha);

        List<ContarboEntity> articulos = contarboRepository.obtenerArticulosOrdenados(request.getEmpresa(),
                request.getBodega(),
                fecha);

        log.info("cantidad articulos {}", articulos.size());

        return articulos;
    }

    private String formatoFechaDDmmYYYY(LocalDateTime input) {
        return input.format(DateTimeFormatter.ofPattern("dd/MM/yyyy"));
    }

    private Map<FicofiuscoEntity, List<ContarboEntity>> generarBloques(List<FicofiuscoEntity> usuarios,
            List<ContarboEntity> articulos) {
        validarParametros(usuarios, articulos);

        List<FicofiuscoEntity> usuariosAleatorios = new ArrayList<>(usuarios);
        Collections.shuffle(usuariosAleatorios);

        Map<FicofiuscoEntity, List<ContarboEntity>> bloques = new LinkedHashMap<>();

        int totalArticulos = articulos.size();
        int totalUsuarios = usuarios.size();

        int articulosPorUsuario = totalArticulos / totalUsuarios;
        int articulosRestantes = totalArticulos % totalUsuarios;

        int indiceInicio = 0;

        for (int i = 0; i < totalUsuarios; i++) {
            int cantidadAsignada = articulosPorUsuario + (i < articulosRestantes ? 1 : 0);
            int indiceFin = indiceInicio + cantidadAsignada;

            List<ContarboEntity> bloque = new ArrayList<>(
                    articulos.subList(indiceInicio, indiceFin));

            bloques.put(usuariosAleatorios.get(i), bloque);

            indiceInicio = indiceFin;
        }
        return bloques;
    }

    private void persistirAsignaciones(Map<FicofiuscoEntity, List<ContarboEntity>> bloques,
            AsignacionConteoRequest request) {
        Long idConteo = ficofiarasRepository.obtenerIdConteo();
        LocalDateTime fechaConteo = LocalDateTime.now();

        List<FicofiarasEntity> asignaciones = construirAsignaciones(bloques, request, idConteo, fechaConteo);

        ficofiarasRepository.saveAll(asignaciones);

    }

    private List<FicofiarasEntity> construirAsignaciones(Map<FicofiuscoEntity, List<ContarboEntity>> bloques,
            AsignacionConteoRequest request,
            Long idConteo,
            LocalDateTime fechaConteo) {
        List<FicofiarasEntity> asignaciones = new ArrayList<>();

        for (Map.Entry<FicofiuscoEntity, List<ContarboEntity>> entry : bloques.entrySet()) {
            FicofiuscoEntity usuario = entry.getKey();

            for (ContarboEntity articulo : entry.getValue()) {
                asignaciones.add(
                        crearAsignacion(
                                usuario,
                                articulo,
                                request,
                                idConteo,
                                fechaConteo));
            }
        }
        return asignaciones;
    }

    private FicofiarasEntity crearAsignacion(FicofiuscoEntity usuario,
            ContarboEntity articulo,
            AsignacionConteoRequest request,
            Long idConteo,
            LocalDateTime fechaConteo) {

        FicofiarasEntity asignacion = new FicofiarasEntity();

        asignacion.setARASIDAS(ficofiarasRepository.obtenerSiguienteId());
        asignacion.setARASIDCO(idConteo);
        asignacion.setARASIDBO(request.getBodega());
        asignacion.setARASIDAR(articulo.getCoabArti());
        asignacion.setARASIDUS(usuario.getUSCOIDUS());
        asignacion.setARASNUCO(NUMERO_CONTEO_INICIAL);// PENDIENTE VALIDAR CONTEOS
        asignacion.setARASCOQR(articulo.getCoabPlac());
        asignacion.setARASCANT(null);
        asignacion.setARASFECO(fechaConteo);
        asignacion.setARASESTA(ESTADO_PENDIENTE);
        asignacion.setARASSINC(SIN_CONTAR);
        asignacion.setARASEMPR(articulo.getCoabEmpr());
        asignacion.setARASPLAC(articulo.getCoabPlac());

        return asignacion;
    }

    private void validarParametros(List<FicofiuscoEntity> usuarios,
            List<ContarboEntity> articulos) {
        if (usuarios == null || usuarios.isEmpty()) {
            throw new IllegalArgumentException("La lista de usuarios no puede ser nula");
        }
        if (articulos == null) {
            throw new IllegalArgumentException("La lista de articulos no puede ser nula");
        }
    }
}
