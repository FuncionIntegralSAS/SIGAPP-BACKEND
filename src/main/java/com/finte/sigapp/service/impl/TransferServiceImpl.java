package com.finte.sigapp.service.impl;

import com.finte.sigapp.dto.request.TransferItemRequest;
import com.finte.sigapp.dto.request.TransferProcessRequest;
import com.finte.sigapp.dto.request.TransferRequest;
import com.finte.sigapp.dto.request.TransferSignRequest;
import com.finte.sigapp.dto.response.ObjectListResponse;
import com.finte.sigapp.dto.response.ObjectResponse;
import com.finte.sigapp.dto.response.TransferCreatedResponse;
import com.finte.sigapp.dto.response.TransferDetailResponse;
import com.finte.sigapp.dto.response.TransferItemResponse;
import com.finte.sigapp.dto.response.TransferResponse;
import com.finte.sigapp.dto.response.TransferSignatureResponse;
import com.finte.sigapp.entity.MotrfirmEntity;
import com.finte.sigapp.entity.MovitrasEntity;
import com.finte.sigapp.exception.BussinessException;
import com.finte.sigapp.exception.catalog.ErrorCode;
import com.finte.sigapp.model.MovitrasTraspasoModel;
import com.finte.sigapp.repository.MotrfirmRepository;
import com.finte.sigapp.repository.MovitrasRepository;
import com.finte.sigapp.repository.TransferJpaRepository;
import com.finte.sigapp.repository.TransferRepository;
import com.finte.sigapp.security.JwtTokenProvider;
import com.finte.sigapp.service.TransferService;
import com.finte.sigapp.utils.FirmaUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Base64;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
public class TransferServiceImpl implements TransferService {

    /** Convencion de la envoltura de respuesta. */
    private static final int CODE_OK = 0;
    private static final int CODE_ERROR = -1;

    private static final String ESTADO_PROCESADOS = "pr";
    private static final String ESTADO_APROBADO = "ap";
    private static final String ESTADO_RECHAZADO = "na";
    private static final String ESTADO_RECIBIDO = "re";

    /** Placa de los elementos que no tienen, igual que ACTIFIJO. */
    private static final String PLACA_VACIA = ".";

    private final TransferRepository repository;
    private final TransferJpaRepository transferJpaRepository;
    private final MovitrasRepository movitrasRepository;
    private final MotrfirmRepository motrfirmRepository;
    private final JwtTokenProvider tokenProvider;
    private final FirmaUtil firmaUtil;
    private final int maxArticulos;

    public TransferServiceImpl(TransferRepository repository,
                               TransferJpaRepository transferJpaRepository,
                               MovitrasRepository movitrasRepository,
                               MotrfirmRepository motrfirmRepository,
                               JwtTokenProvider tokenProvider,
                               FirmaUtil firmaUtil,
                               @Value("${app.traspasos.max-articulos:50}") int maxArticulos) {
        this.repository = repository;
        this.transferJpaRepository = transferJpaRepository;
        this.movitrasRepository = movitrasRepository;
        this.motrfirmRepository = motrfirmRepository;
        this.tokenProvider = tokenProvider;
        this.firmaUtil = firmaUtil;
        this.maxArticulos = maxArticulos;
    }

    /**
     * Abre el traspaso con el primer articulo y anade el resto uno a uno.
     *
     * Son N llamadas al paquete en vez de una con lista porque el documento
     * REQUSUMI necesita datos que salen del activo y no se puede crear antes de
     * conocer al menos uno. Todas van dentro de esta transaccion: si falla el
     * tercer articulo no queda nada, ni siquiera el documento.
     */
    @Override
    @Transactional(rollbackFor = Exception.class)
    public ObjectResponse crearSolicitud(TransferRequest request, String bearerToken) {

        String usuarioCrea = obtenerUsuario(bearerToken);

        if (request.getPersonaFuente().equals(request.getPersonaDestino())) {
            return new ObjectResponse(CODE_ERROR,
                    "La persona fuente y la persona destino no pueden ser la misma");
        }

        List<TransferItemRequest> articulos = request.getArticulos();

        if (articulos.size() > maxArticulos) {
            return new ObjectResponse(CODE_ERROR,
                    "Un traspaso no puede incluir mas de " + maxArticulos + " articulos");
        }

        String repetido = buscarRepetido(articulos);
        if (repetido != null) {
            return new ObjectResponse(CODE_ERROR,
                    "El articulo " + repetido + " esta repetido en la solicitud");
        }

        TransferItemRequest primero = articulos.get(0);

        TransferCreatedResponse creado = repository.crearSolicitud(
                request.getEmpresa(),
                request.getPersonaFuente(),
                request.getPersonaDestino(),
                primero.getArticulo(),
                normalizarPlaca(primero.getPlaca()),
                request.getObservacion(),
                request.getTipoMovimiento(),
                usuarioCrea);

        if (creado == null) {
            return new ObjectResponse(CODE_ERROR,
                    "El traspaso se proceso pero el procedimiento no retorno el numero de tramite");
        }

        // El resto de articulos cuelga del tramite recien creado. El paquete valida
        // aqui que compartan centro de informacion y tercero con el primero.
        for (TransferItemRequest articulo : articulos.subList(1, articulos.size())) {
            repository.agregarArticulo(
                    creado.getId(),
                    articulo.getArticulo(),
                    normalizarPlaca(articulo.getPlaca()),
                    usuarioCrea);
        }

        log.info("Traspaso {} creado con {} articulo(s) sobre el documento {}-{}-{}",
                creado.getId(), articulos.size(), creado.getEmpresaDocumento(),
                creado.getTipoDocumento(), creado.getNumeroDocumento());

        return new ObjectResponse(CODE_OK, "El traspaso fue creado correctamente", creado);
    }

    @Override
    public ObjectListResponse listarSolicitudes(String empresa, String bodega, String estado) {

        List<String> estadosDb;

        if (ESTADO_PROCESADOS.equalsIgnoreCase(estado)) {
            // "Procesados" agrupa todo lo que ya salio de la bandeja de pendientes.
            // Son tres y no cinco: af y ad desaparecieron al quitar el orden de las
            // firmas, que ahora se consultan en FI_MOTRFIRM.
            estadosDb = Arrays.asList(ESTADO_APROBADO, ESTADO_RECHAZADO, ESTADO_RECIBIDO);
        } else {
            estadosDb = Collections.singletonList(estado);
        }

        log.info("Listando traspasos en estados {} - empresa {}, bodega {}", estadosDb, empresa, bodega);

        // ObjectListResponse expone List<Object>, y List<TransferResponse> no es
        // asignable a esa lista: se copia a una ArrayList<Object>.
        List<Object> traspasos = new ArrayList<>(
                transferJpaRepository.buscarPorEstados(estadosDb, empresa, bodega).stream()
                        .map(this::mapToResponse)
                        .toList());

        return new ObjectListResponse(CODE_OK, "Consulta exitosa", traspasos);
    }

    @Override
    public ObjectResponse obtenerDetalle(Long numeroTramite) {

        List<MovitrasEntity> lineas = movitrasRepository.findByMotrnutrOrderByMotridmt(numeroTramite);

        if (lineas.isEmpty()) {
            return new ObjectResponse(CODE_ERROR, "No existe el traspaso " + numeroTramite);
        }

        List<MotrfirmEntity> firmas = motrfirmRepository.findByMofinutrOrderByMofiposi(numeroTramite);

        return new ObjectResponse(CODE_OK, "Consulta exitosa", mapToDetail(lineas, firmas));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ObjectResponse procesarTraspaso(Long numeroTramite,
                                           TransferProcessRequest request,
                                           String bearerToken) {

        String usuarioActualiza = obtenerUsuario(bearerToken);

        if (ESTADO_RECHAZADO.equals(request.getEstado()) && !StringUtils.hasText(request.getObservacion())) {
            return new ObjectResponse(CODE_ERROR, "Debe incluir una observacion al rechazar el traspaso");
        }

        // El paquete aplica el estado a las N lineas: el tramite se aprueba o se
        // rechaza completo, nunca por articulo.
        repository.aprobarRechazar(
                numeroTramite,
                request.getEstado(),
                request.getObservacion(),
                usuarioActualiza);

        return new ObjectResponse(CODE_OK, "El trámite fue procesado correctamente");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ObjectResponse registrarFirma(Long numeroTramite, TransferSignRequest request, String bearerToken) {

        byte[] firma;
        try {
            // El base64 llega del canvas del front: puede traer prefijo data URL y no
            // hay garantia de que sea una imagen. Se normaliza antes de tocar la BD.
            firma = firmaUtil.decodificar(request.getFirma());
        } catch (BussinessException e) {
            log.warn("Firma rechazada para el traspaso {}: {}", numeroTramite, e.getMessage());
            return new ObjectResponse(CODE_ERROR, e.getMessage());
        }

        // El estado del tramite y que esa posicion no este ya firmada los valida el
        // paquete. No hay orden entre las dos firmas.
        repository.registrarFirma(
                numeroTramite,
                request.getTipoFirma(),
                firma,
                obtenerUsuario(bearerToken));

        return new ObjectResponse(CODE_OK, "La firma fue registrada correctamente");
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ObjectResponse recibir(Long numeroTramite, String bearerToken) {

        // Es aqui donde el traspaso llega al inventario: el paquete exige las dos
        // firmas y genera el documento con una linea por articulo.
        repository.recibir(numeroTramite, obtenerUsuario(bearerToken));

        return new ObjectResponse(CODE_OK, "La recepción fue registrada correctamente");
    }

    @Override
    public byte[] obtenerFirma(Long numeroTramite, String tipoFirma) {

        int posicion = posicionDe(tipoFirma);

        return motrfirmRepository.findByMofinutrAndMofiposi(numeroTramite, posicion)
                .map(MotrfirmEntity::getMofifipe)
                .orElseGet(() -> {
                    log.warn("Se pidio la firma {} de un traspaso sin esa posicion: {}",
                            tipoFirma, numeroTramite);
                    return null;
                });
    }

    /**
     * Traduce el tipo de firma que usan los endpoints a la posicion de
     * FI_MOTRFIRM.
     */
    private int posicionDe(String tipoFirma) {
        String tipo = tipoFirma == null ? "" : tipoFirma.trim().toUpperCase();

        if (TransferRepository.FIRMA_FUENTE.equals(tipo)) {
            return MotrfirmEntity.POSICION_FUENTE;
        }
        if (TransferRepository.FIRMA_DESTINO.equals(tipo)) {
            return MotrfirmEntity.POSICION_DESTINO;
        }
        throw new BussinessException(ErrorCode.SIGAPP_400,
                "El tipo de firma solo puede ser 'FU' (fuente) o 'DE' (destino)");
    }

    /**
     * Primer articulo que aparece dos veces en la solicitud, o null si no hay.
     *
     * El paquete tambien lo rechaza y la constraint de la tabla tambien, pero
     * cortarlo aqui evita abrir un documento REQUSUMI que despues habria que
     * deshacer.
     */
    private String buscarRepetido(List<TransferItemRequest> articulos) {
        Set<String> vistos = new HashSet<>();

        for (TransferItemRequest articulo : articulos) {
            if (!vistos.add(articulo.clave())) {
                return articulo.getArticulo();
            }
        }
        return null;
    }

    /** Los elementos sin placa la guardan como punto, igual que ACTIFIJO. */
    private String normalizarPlaca(String placa) {
        return StringUtils.hasText(placa) ? placa.trim() : PLACA_VACIA;
    }

    /**
     * Usuario que queda registrado en FI_MOVITRAS (MOTRUSCR / MOTRUSAP). Es el
     * subject del token, que para el login administrativo es usbdcodi.
     */
    private String obtenerUsuario(String bearerToken) {
        String token = tokenProvider.extraerToken(bearerToken);
        return tokenProvider.getUsernameFromJWT(token);
    }

    /**
     * La bandeja lista y enlaza; todo lo demas lo sirve
     * {@link #obtenerDetalle(Long)}.
     *
     * El {@code id} es MOTRNUTR, el numero de tramite, y es lo que va en
     * {@code {id}} del resto de rutas. No confundirlo con
     * {@code numeroDocumento}, que es el RESUNUME de la requisicion en el ERP:
     * son dos secuencias distintas.
     */
    private TransferResponse mapToResponse(MovitrasTraspasoModel model) {
        return TransferResponse.builder()
                .id(model.getMotrnutr())
                .tipoDocumento(model.getMotrdrtd())
                .numeroDocumento(model.getMotrdrnd())
                .fechaCreacion(model.getMotrfecr())
                .build();
    }

    /**
     * Arma el detalle a partir de las N lineas y las dos firmas.
     *
     * Los datos de cabecera se toman de la primera linea: de MOTRDREM a MOTRFERE
     * todas guardan lo mismo, y el paquete se encarga de que siga siendo cierto.
     */
    private TransferDetailResponse mapToDetail(List<MovitrasEntity> lineas, List<MotrfirmEntity> firmas) {

        MovitrasEntity cabecera = lineas.get(0);

        return TransferDetailResponse.builder()
                .id(cabecera.getMotrnutr())
                .estado(cabecera.getMotresta())
                .empresaDocumento(cabecera.getMotrdrem())
                .tipoDocumento(cabecera.getMotrdrtd())
                .numeroDocumento(cabecera.getMotrdrnd())
                .personaFuente(cabecera.getMotrpefu())
                .personaDestino(cabecera.getMotrpede())
                .observacion(cabecera.getMotrobse())
                .motivoRechazo(cabecera.getMotrmore())
                .fechaCreacion(cabecera.getMotrfecr())
                .usuarioCreacion(cabecera.getMotruscr())
                .fechaAprobacion(cabecera.getMotrfeap())
                .usuarioAprobacion(cabecera.getMotrusap())
                .fechaRecibe(cabecera.getMotrfere())
                .articulos(lineas.stream().map(this::mapToItem).toList())
                .firmas(firmas.stream().map(this::mapToSignature).toList())
                .build();
    }

    private TransferItemResponse mapToItem(MovitrasEntity linea) {
        return TransferItemResponse.builder()
                .articulo(linea.getMotrarti())
                .placa(linea.getMotrplac())
                .build();
    }

    private TransferSignatureResponse mapToSignature(MotrfirmEntity firma) {
        boolean esFuente = firma.getMofiposi() != null
                && firma.getMofiposi() == MotrfirmEntity.POSICION_FUENTE;

        return TransferSignatureResponse.builder()
                .posicion(firma.getMofiposi())
                .tipo(esFuente ? TransferRepository.FIRMA_FUENTE : TransferRepository.FIRMA_DESTINO)
                .fechaFirma(firma.getMofifefi())
                .firmada(firma.estaFirmada())
                .firma(aBase64(firma))
                .build();
    }

    /**
     * Las firmas se guardan como bytes y el detalle las expone en base64: asi el
     * front las mete directo en un {@code <img src="data:...">} y pinta la
     * pantalla con la peticion que ya hizo, sin el fetch autenticado ni el object
     * URL que exigiria {@link #obtenerFirma(Long, String)}.
     */
    private String aBase64(MotrfirmEntity firma) {
        if (firma == null || !firma.estaFirmada()) {
            return null;
        }
        return Base64.getEncoder().encodeToString(firma.getMofifipe());
    }
}
