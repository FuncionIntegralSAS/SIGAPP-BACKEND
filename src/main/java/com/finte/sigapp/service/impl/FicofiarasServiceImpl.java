package com.finte.sigapp.service.impl;

import com.finte.sigapp.dto.request.ArticuloConteo;
import com.finte.sigapp.dto.request.AsignacionConteoRequest;
import com.finte.sigapp.dto.request.ReporteConteoRequest;
import com.finte.sigapp.dto.response.ConteoFisicoResponse;
import com.finte.sigapp.entity.ContarboEntity;
import com.finte.sigapp.entity.FicofiarasEntity;
import com.finte.sigapp.entity.FicofiuscoEntity;
import com.finte.sigapp.exception.BussinessException;
import com.finte.sigapp.exception.catalog.ErrorCode;
import com.finte.sigapp.repository.ContarboRepository;
import com.finte.sigapp.repository.FicofiarasRepository;
import com.finte.sigapp.service.EmailService;
import com.finte.sigapp.service.FicofiarasService;
import com.finte.sigapp.service.FicofiuscoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
@Slf4j
public class FicofiarasServiceImpl implements FicofiarasService {

    private static final String ESTADO_PENDIENTE = "pe";
    private static final String SIN_CONTAR = "N";
    private static final Long NUMERO_CONTEO_INICIAL = 1L;

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
            throw new IllegalArgumentException(ErrorCode.SIGAPP_004.getMessage());
        }
        if (articulos == null) {
            throw new IllegalArgumentException(ErrorCode.SIGAPP_005.getMessage());
        }
    }

    @Override
    public ConteoFisicoResponse reportarConteo(Long userId, ReporteConteoRequest request) {
        log.info("reportarConteo init - userId {},bodega: {}, numeroConteo: {}",
                userId, request.getBodega(), request.getNumeroConteo());

        validarNumeroConteo(request.getNumeroConteo());

        Map<Long, FicofiarasEntity> pendientesPorArticulo = obtenerPendientesComoMapa(
                userId, request.getBodega(), request.getNumeroConteo());
        log.warn("cantidad articulos pendientes {}", pendientesPorArticulo.size());
        if (pendientesPorArticulo.isEmpty()) {
            log.debug("entro a la excepcion");
            throw new BussinessException(
                    ErrorCode.SIGAPP_002,
                    String.format("Usuario %d no tiene articulos pendientes para el conteo %d en la bodega %s",
                            userId,
                            request.getNumeroConteo(),
                            request.getBodega()));
        }

        validarArticulosExistentes(request.getArticulos(), pendientesPorArticulo);

        List<FicofiarasEntity> entidadesActualizadas = aplicarConteoALista(
                request.getArticulos(), pendientesPorArticulo, request.getNumeroConteo());

        ficofiarasRepository.saveAll(entidadesActualizadas);

        log.info("reportarConteo OK - {} artículos actualizados", entidadesActualizadas.size());

        asentarEncontarbo(entidadesActualizadas, request.getBodega(), request.getNumeroConteo());
        return ConteoFisicoResponse.builder()
                .success(true)
                .message("Conteo " + request.getNumeroConteo() + " reportado exitosamente — "
                        + entidadesActualizadas.size() + " artículos actualizados")
                .build();
    }

    private Map<Long, FicofiarasEntity> obtenerPendientesComoMapa(Long userId,
            String BodegaId,
            Integer numeroConteo) {

        return ficofiarasRepository.findByUserBodega(userId, BodegaId)
                .stream()
                .filter(e -> !estadoContado(e, numeroConteo))
                .filter(e -> e.getARASIDAR() != null)
                .collect(Collectors.toMap(
                        FicofiarasEntity::getARASIDAR,
                        e -> e,
                        (e1, e2) -> e1 // valida duplicados se queda con el primero
                ));
    }

    private void validarNumeroConteo(Integer numeroConteo) {
        if (numeroConteo == null || numeroConteo < 1 || numeroConteo > 3) {
            // throw new BussinessException("Numero de conteo invalido");
            throw new BussinessException(ErrorCode.SIGAPP_001);
        }
    }

    private boolean estadoContado(FicofiarasEntity entity, Integer numeroConteo) {
        return switch (numeroConteo) {
            case 1 -> entity.getARASCANT() != null;
            case 2 -> entity.getARASCNT2() != null;
            case 3 -> entity.getARASCNT3() != null;
            default -> false;
        };

    }

    private void validarArticulosExistentes(List<ArticuloConteo> articulos,
            Map<Long, FicofiarasEntity> pendientes) {

        List<Long> noEncontrados = articulos.stream()
                .map(ArticuloConteo::getIdArticulo)
                .filter(Objects::nonNull)
                .filter(id -> !pendientes.containsKey(id))
                .collect(Collectors.toList());

        if (!noEncontrados.isEmpty()) {
            // throw new BussinessException("Articulos no encontrados: " + noEncontrados);
            throw new BussinessException(ErrorCode.SIGAPP_002,
                    String.format("Articulos no encontrados: %s", noEncontrados.toString()));
        }
    }

    private List<FicofiarasEntity> aplicarConteoALista(List<ArticuloConteo> articulos,
            Map<Long, FicofiarasEntity> pendientes,
            Integer numeroConteo) {

        LocalDateTime now = LocalDateTime.now();

        return articulos.stream()
                .map(articulo -> aplicarConteo(
                        pendientes.get(articulo.getIdArticulo()),
                        articulo.getCantidad(),
                        numeroConteo,
                        now))
                .collect(Collectors.toList());
    }

    private FicofiarasEntity aplicarConteo(FicofiarasEntity entity,
            Long cantidad,
            Integer numeroConteo,
            LocalDateTime now) {
        switch (numeroConteo) {
            case 1 -> {
                entity.setARASCANT(cantidad);
                entity.setARASFESI(now);
            }
            case 2 -> {
                entity.setARASCNT2(cantidad);
                entity.setARASFEC2(now);
            }
            case 3 -> {
                entity.setARASCNT3(cantidad);
                entity.setARASFEC3(now);
            }
        }
        return entity;
    }

    private void asentarEncontarbo(List<FicofiarasEntity> entidades,
            String idBodega,
            Integer numeroConteo) {
        log.info("asentarEnContarbo ini - {} articulos, conteo: {}", entidades.size(), numeroConteo);

        List<Long> idArticulos = entidades.stream()
                .map(FicofiarasEntity::getARASIDAR)
                .filter(Objects::nonNull)
                .collect(Collectors.toList());

        Map<Long, ContarboEntity> contarboMap = contarboRepository
                .obtenerArticulosOrdenadosContarbo(idBodega)
                .stream()
                .collect(Collectors.toMap(ContarboEntity::getCoabArti,
                        c -> c,
                        (c1, c2) -> c1));

        List<Long> noEncontrados = idArticulos.stream()
                .filter(id -> !contarboMap.containsKey(id))
                .collect(Collectors.toList());

        if (!noEncontrados.isEmpty()) {
            log.warn("Articulos no encontrdos en contarbo {}", noEncontrados);
            throw new BussinessException(
                    ErrorCode.SIGAPP_006,
                    String.format("Articulos no encontrados en contarbo : %s", noEncontrados.toString()));
        }

        LocalDateTime now = LocalDateTime.now();

        entidades.forEach(e -> {
            ContarboEntity contarbo = contarboMap.get(e.getARASIDAR());
            aplicarCantidad(contarbo, getCantidad(e, numeroConteo), numeroConteo);
            marcarSincronizacion(e, numeroConteo, now);
        });

        contarboRepository.saveAll(contarboMap.values().stream()
                .collect(Collectors.toList()));

        ficofiarasRepository.saveAll(entidades);

        log.info("asentarEnContarbo OK - {} articulos asentados", entidades.size());
    }

    private void aplicarCantidad(ContarboEntity c, Long cantidad, Integer numeroconteo) {
        BigDecimal valor = cantidad != null ? BigDecimal.valueOf(cantidad) : null;

        switch (numeroconteo) {
            case 1 -> c.setCoabCac1(valor);
            case 2 -> c.setCoabCac2(valor);
            case 3 -> c.setCoabCac3(valor);
        }
    }

    private Long getCantidad(FicofiarasEntity c, Integer numeroConteo) {
        return switch (numeroConteo) {
            case 1 -> c.getARASCANT();
            case 2 -> c.getARASCNT2();
            case 3 -> c.getARASCNT3();
            default -> null;
        };
    }

    private void marcarSincronizacion(FicofiarasEntity e, Integer numeroConteo, LocalDateTime now) {
        switch (numeroConteo) {
            case 1 -> {
                e.setARASFESI(now);
                e.setARASSINC("S");
            }
            case 2 -> {
                e.setARASFES2(now);
                e.setARASSIN2("S");
            }
            case 3 -> {
                e.setARASFES3(now);
                e.setARASSIN3("S");
            }
        }
    }

}
