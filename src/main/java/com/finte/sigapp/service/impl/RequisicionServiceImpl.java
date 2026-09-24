package com.finte.sigapp.service.impl;

import com.finte.sigapp.dto.request.RequisicionAnularRequest;
import com.finte.sigapp.dto.request.RequisicionFirmaRequest;
import com.finte.sigapp.dto.request.RequisicionLineaClaveRequest;
import com.finte.sigapp.dto.request.RequisicionLineaRequest;
import com.finte.sigapp.dto.request.RequisicionLineasRequest;
import com.finte.sigapp.dto.request.RequisicionRegistrarRequest;
import com.finte.sigapp.dto.response.ObjectListResponse;
import com.finte.sigapp.dto.response.ObjectResponse;
import com.finte.sigapp.dto.response.RequisicionDetailResponse;
import com.finte.sigapp.dto.response.RequisicionFirmaResponse;
import com.finte.sigapp.dto.response.RequisicionLineaResponse;
import com.finte.sigapp.dto.response.RequisicionResponse;
import com.finte.sigapp.dto.response.RequisicionSalidaResponse;
import com.finte.sigapp.dto.response.RequisicionTipoResponse;
import com.finte.sigapp.entity.MoviresuEntity;
import com.finte.sigapp.entity.RequfirmEntity;
import com.finte.sigapp.exception.BussinessException;
import com.finte.sigapp.exception.catalog.ErrorCode;
import com.finte.sigapp.model.PlacaRequisicionModel;
import com.finte.sigapp.model.RequisicionCabeceraModel;
import com.finte.sigapp.repository.MoviresuRepository;
import com.finte.sigapp.repository.PlacaRequisicionRepository;
import com.finte.sigapp.repository.RequfirmRepository;
import com.finte.sigapp.repository.RequisicionBandejaRepository;
import com.finte.sigapp.repository.RequisicionCabeceraRepository;
import com.finte.sigapp.repository.RequisicionRepository;
import com.finte.sigapp.repository.TipoRequisicionRepository;
import com.finte.sigapp.security.JwtTokenProvider;
import com.finte.sigapp.service.RequisicionService;
import com.finte.sigapp.utils.FirmaUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Ciclo de vida de las requisiciones de suministro.
 *
 * DONDE VIVE CADA COSA
 *
 * Las lecturas se resuelven con consultas nativas y las escrituras con
 * PKG_FI_REQUISICION, ninguna con JPA. El motivo no es estilistico: cada UPDATE
 * sobre MOVIRESU dispara un trigger del ERP que valida disponibilidad, placas y
 * saldos y propaga a ARTIBODE y SALDINLA, y el paquete ademas bloquea la
 * requisicion entera antes de tocar nada. Escribir desde Hibernate saltaria ese
 * bloqueo.
 *
 * COMO SE MANEJAN LOS ERRORES, QUE AQUI NO ES OBVIO
 *
 * Las validaciones que se hacen ANTES de escribir devuelven
 * {@code ObjectResponse(-1, ...)} directamente: no hay nada que revertir.
 *
 * Los errores que vienen del ERP se dejan PROPAGAR a proposito. Si se
 * capturaran aqui para convertirlos en {@code code -1}, la transaccion no
 * marcaria rollback y las lineas ya procesadas quedarian confirmadas mientras la
 * respuesta dice que fallo. El controller los traduce a un mensaje legible con
 * OracleErrorUtil despues de que Spring haya revertido.
 */
@Slf4j
@Service
public class RequisicionServiceImpl implements RequisicionService {

    private static final int CODE_OK = 0;
    private static final int CODE_ERROR = -1;

    private final RequisicionRepository requisicionRepository;
    private final RequisicionBandejaRepository bandejaRepository;
    private final RequisicionCabeceraRepository cabeceraRepository;
    private final MoviresuRepository moviresuRepository;
    private final RequfirmRepository requfirmRepository;
    private final PlacaRequisicionRepository placaRepository;
    private final TipoRequisicionRepository tipoRepository;
    private final JwtTokenProvider tokenProvider;
    private final FirmaUtil firmaUtil;
    private final int maxLineas;
    private final int diasBandeja;
    private final int maxBandeja;

    /**
     * Constructor explicito, no {@code @RequiredArgsConstructor}: las tres
     * propiedades entran por aqui para poder construir el service en un test sin
     * levantar el contexto de Spring.
     */
    public RequisicionServiceImpl(RequisicionRepository requisicionRepository,
                                  RequisicionBandejaRepository bandejaRepository,
                                  RequisicionCabeceraRepository cabeceraRepository,
                                  MoviresuRepository moviresuRepository,
                                  RequfirmRepository requfirmRepository,
                                  PlacaRequisicionRepository placaRepository,
                                  TipoRequisicionRepository tipoRepository,
                                  JwtTokenProvider tokenProvider,
                                  FirmaUtil firmaUtil,
                                  @Value("${app.requisiciones.max-lineas:200}") int maxLineas,
                                  @Value("${app.requisiciones.dias-bandeja:90}") int diasBandeja,
                                  @Value("${app.requisiciones.max-bandeja:500}") int maxBandeja) {

        this.requisicionRepository = requisicionRepository;
        this.bandejaRepository = bandejaRepository;
        this.cabeceraRepository = cabeceraRepository;
        this.moviresuRepository = moviresuRepository;
        this.requfirmRepository = requfirmRepository;
        this.placaRepository = placaRepository;
        this.tipoRepository = tipoRepository;
        this.tokenProvider = tokenProvider;
        this.firmaUtil = firmaUtil;
        this.maxLineas = maxLineas;
        this.diasBandeja = diasBandeja;
        this.maxBandeja = maxBandeja;
    }

    @Override
    public ObjectListResponse listarTipos() {
        List<Object> tipos = new ArrayList<>(tipoRepository.buscarGestionables().stream()
                .map(tipo -> (Object) RequisicionTipoResponse.builder()
                        .codigo(tipo.getCodigo())
                        .nombre(tipo.getNombre())
                        .build())
                .toList());

        if (tipos.isEmpty()) {
            log.warn("No hay tipos de requisicion gestionables: falta cargar la equivalencia COBO/REQU en EQUIVALE");
        }

        return new ObjectListResponse(CODE_OK, "Consulta exitosa", tipos);
    }

    @Override
    public ObjectListResponse listarRequisiciones(String estado, String empresa, String tipoDocumento,
                                                  String bodega, LocalDate desde) {

        LocalDate ventana = desde != null ? desde : LocalDate.now().minusDays(diasBandeja);

        log.info("Listando requisiciones en estado {} desde {}", estado, ventana);

        List<Object> requisiciones = new ArrayList<>(
                bandejaRepository.buscarBandeja(estado, empresa, tipoDocumento, bodega, ventana, maxBandeja).stream()
                        .map(fila -> (Object) RequisicionResponse.builder()
                                .empresa(fila.getEmpresa())
                                .tipoDocumento(fila.getTipoDocumento())
                                .numero(fila.getNumero())
                                .estado(fila.getEstado())
                                .fecha(fila.getFecha())
                                .bodega(fila.getBodega())
                                .lineas(fila.getLineas())
                                .build())
                        .toList());

        return new ObjectListResponse(CODE_OK, "Consulta exitosa", requisiciones);
    }

    @Override
    public ObjectResponse obtenerDetalle(String empresa, String tipoDocumento, Long numero) {
        BigDecimal numeroDocumento = BigDecimal.valueOf(numero);

        Optional<RequisicionCabeceraModel> cabecera =
                cabeceraRepository.buscarCabecera(empresa, tipoDocumento, numeroDocumento);

        if (cabecera.isEmpty()) {
            return new ObjectResponse(CODE_ERROR, "No existe la requisicion " + referencia(empresa, tipoDocumento, numero));
        }

        List<MoviresuEntity> lineas = moviresuRepository
                .findByMorstdemAndMorstidoAndMorsrenuOrderByMorsartiAscMorsbodeAscMorssecuAsc(
                        empresa, tipoDocumento, numeroDocumento);

        Map<String, List<String>> placas = agruparPlacas(empresa, tipoDocumento, numeroDocumento);

        List<RequfirmEntity> firmas = requfirmRepository
                .findByRefiemprAndRefitidoAndRefinumeOrderByRefiposi(empresa, tipoDocumento, numeroDocumento);

        RequisicionDetailResponse detalle = mapearDetalle(cabecera.get(), lineas, placas, firmas);

        return new ObjectResponse(CODE_OK, "Consulta exitosa", detalle);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ObjectResponse aprobar(String empresa, String tipoDocumento, Long numero,
                                  RequisicionLineasRequest request, String bearerToken) {

        ObjectResponse rechazo = validarLineas(request.getLineas());

        if (rechazo != null) {
            return rechazo;
        }

        String usuario = obtenerUsuario(bearerToken);
        requisicionRepository.fijarContexto(usuario);

        for (RequisicionLineaRequest linea : request.getLineas()) {
            requisicionRepository.aprobarLinea(empresa, tipoDocumento, numero,
                    linea.getArticulo(), linea.getBodega(), linea.getSecuencia(),
                    linea.getCantidad(), usuario);
        }

        log.info("Requisicion {} aprobada por {} en {} linea(s)",
                referencia(empresa, tipoDocumento, numero), usuario, request.getLineas().size());

        return new ObjectResponse(CODE_OK,
                "Se aprobaron " + request.getLineas().size() + " linea(s) de la requisicion "
                        + referencia(empresa, tipoDocumento, numero));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ObjectResponse entregar(String empresa, String tipoDocumento, Long numero,
                                   RequisicionLineasRequest request, String bearerToken) {

        ObjectResponse rechazo = validarLineas(request.getLineas());

        if (rechazo != null) {
            return rechazo;
        }

        String usuario = obtenerUsuario(bearerToken);
        requisicionRepository.fijarContexto(usuario);

        for (RequisicionLineaRequest linea : request.getLineas()) {

            // Las placas van ANTES de entregar: el trigger del ERP comprueba que
            // su numero coincida con la cantidad entregada en el mismo UPDATE.
            asignarPlacas(empresa, tipoDocumento, numero, linea);

            requisicionRepository.entregarLinea(empresa, tipoDocumento, numero,
                    linea.getArticulo(), linea.getBodega(), linea.getSecuencia(),
                    linea.getCantidad(), usuario);
        }

        log.info("Requisicion {} entregada por {} en {} linea(s)",
                referencia(empresa, tipoDocumento, numero), usuario, request.getLineas().size());

        return new ObjectResponse(CODE_OK,
                "Se entregaron " + request.getLineas().size() + " linea(s) de la requisicion "
                        + referencia(empresa, tipoDocumento, numero));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ObjectResponse anular(String empresa, String tipoDocumento, Long numero,
                                 RequisicionAnularRequest request, String bearerToken) {

        if (request.getLineas().size() > maxLineas) {
            return new ObjectResponse(CODE_ERROR,
                    "No se pueden procesar mas de " + maxLineas + " lineas en una sola peticion");
        }

        String usuario = obtenerUsuario(bearerToken);
        requisicionRepository.fijarContexto(usuario);

        for (RequisicionLineaClaveRequest linea : request.getLineas()) {
            requisicionRepository.anularLinea(empresa, tipoDocumento, numero,
                    linea.getArticulo(), linea.getBodega(), linea.getSecuencia(), usuario);
        }

        log.info("Requisicion {}: {} linea(s) anuladas al entregar por {}",
                referencia(empresa, tipoDocumento, numero), request.getLineas().size(), usuario);

        return new ObjectResponse(CODE_OK,
                "Se anularon " + request.getLineas().size() + " linea(s) de la requisicion "
                        + referencia(empresa, tipoDocumento, numero));
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ObjectResponse registrarFirma(String empresa, String tipoDocumento, Long numero,
                                         RequisicionFirmaRequest request, String bearerToken) {

        byte[] firma;

        try {
            // Se decodifica antes de tocar nada: si el base64 no es valido, la
            // peticion se rechaza sin haber escrito ni el contexto de sesion.
            firma = firmaUtil.decodificar(request.getFirma());
        } catch (BussinessException e) {
            log.warn("Firma rechazada para la requisicion {}: {}",
                    referencia(empresa, tipoDocumento, numero), e.getMessage());
            return new ObjectResponse(CODE_ERROR, e.getMessage());
        }

        String usuario = obtenerUsuario(bearerToken);
        requisicionRepository.fijarContexto(usuario);

        requisicionRepository.registrarFirma(empresa, tipoDocumento, numero,
                request.getTipo(), request.getPersona(), firma, usuario);

        log.info("Firma {} registrada en la requisicion {} por {}",
                request.getTipo(), referencia(empresa, tipoDocumento, numero), usuario);

        return new ObjectResponse(CODE_OK, "La firma fue registrada correctamente");
    }

    @Override
    public byte[] obtenerFirma(String empresa, String tipoDocumento, Long numero, String tipoFirma) {
        int posicion = posicionDe(tipoFirma);

        return requfirmRepository
                .findByRefiemprAndRefitidoAndRefinumeAndRefiposi(
                        empresa, tipoDocumento, BigDecimal.valueOf(numero), posicion)
                .map(RequfirmEntity::getRefifipe)
                .orElse(null);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public ObjectResponse registrarSalida(String empresa, String tipoDocumento, Long numero,
                                          RequisicionRegistrarRequest request, String bearerToken) {

        List<RequisicionLineaRequest> parciales =
                request != null && request.getLineas() != null ? request.getLineas() : List.of();

        if (parciales.size() > maxLineas) {
            return new ObjectResponse(CODE_ERROR,
                    "No se pueden procesar mas de " + maxLineas + " lineas en una sola peticion");
        }

        String usuario = obtenerUsuario(bearerToken);
        requisicionRepository.fijarContexto(usuario);

        // Las recepciones parciales se sellan una a una. Lo que no venga en el
        // payload lo completa el paquete con la cantidad entregada.
        for (RequisicionLineaRequest linea : parciales) {
            soltarPlacasNoRecibidas(empresa, tipoDocumento, numero, linea);

            requisicionRepository.recibirLinea(empresa, tipoDocumento, numero,
                    linea.getArticulo(), linea.getBodega(), linea.getSecuencia(),
                    linea.getCantidad(), usuario);
        }

        Map<String, Object> salida = requisicionRepository.generarSalida(empresa, tipoDocumento, numero, usuario);

        RequisicionSalidaResponse resultado = RequisicionSalidaResponse.builder()
                .tipoSalida(texto(salida.get(RequisicionRepository.SALIDA_TIPO_DOCUMENTO)))
                .mensaje(texto(salida.get(RequisicionRepository.SALIDA_MENSAJE)))
                .build();

        log.info("Requisicion {} registrada por {} con tipo de salida {}",
                referencia(empresa, tipoDocumento, numero), usuario, resultado.getTipoSalida());

        return new ObjectResponse(CODE_OK, "La salida fue generada correctamente", resultado);
    }

    /**
     * Asigna las placas de una linea antes de entregarla. Sin placas no hace
     * nada: el articulo puede ser de consumo, y es el ERP quien sabe cuales lo
     * son.
     */
    private void asignarPlacas(String empresa, String tipoDocumento, Long numero, RequisicionLineaRequest linea) {
        if (linea.getPlacas() == null) {
            return;
        }

        for (String placa : linea.getPlacas()) {
            requisicionRepository.asignarPlaca(empresa, tipoDocumento, numero,
                    linea.getArticulo(), linea.getSecuencia(), placa);
        }
    }

    /**
     * En una recepcion parcial de un devolutivo hay que soltar las placas que no
     * se reciben: el ERP compara el numero de placas con la cantidad recibida y
     * falla si sobran.
     *
     * El front manda las que SI se reciben, que es lo natural para quien esta
     * revisando la entrega; aqui se calcula la diferencia.
     */
    private void soltarPlacasNoRecibidas(String empresa, String tipoDocumento, Long numero,
                                         RequisicionLineaRequest linea) {

        if (linea.getPlacas() == null || linea.getPlacas().isEmpty()) {
            return;
        }

        List<String> asignadas = placaRepository
                .buscarPorRequisicion(empresa, tipoDocumento, BigDecimal.valueOf(numero))
                .stream()
                .filter(placa -> placa.getArticulo().equals(linea.getArticulo())
                        && placa.getSecuencia().compareTo(linea.getSecuencia()) == 0)
                .map(PlacaRequisicionModel::getPlaca)
                .toList();

        for (String asignada : asignadas) {
            if (!linea.getPlacas().contains(asignada)) {
                requisicionRepository.soltarPlaca(empresa, tipoDocumento, numero,
                        linea.getArticulo(), linea.getSecuencia(), asignada);
            }
        }
    }

    /** Validaciones comunes de aprobar y entregar, antes de escribir nada. */
    private ObjectResponse validarLineas(List<RequisicionLineaRequest> lineas) {
        if (lineas.size() > maxLineas) {
            return new ObjectResponse(CODE_ERROR,
                    "No se pueden procesar mas de " + maxLineas + " lineas en una sola peticion");
        }

        String repetida = buscarRepetida(lineas);

        if (repetida != null) {
            return new ObjectResponse(CODE_ERROR, "La linea " + repetida + " viene repetida en la peticion");
        }

        return null;
    }

    /**
     * Dos entradas para la misma linea significan que el cliente se equivoco: la
     * segunda pisaria a la primera y el resultado dependeria del orden.
     */
    private String buscarRepetida(List<RequisicionLineaRequest> lineas) {
        List<String> vistas = new ArrayList<>();

        for (RequisicionLineaRequest linea : lineas) {
            String clave = claveDe(linea.getArticulo(), linea.getBodega(), linea.getSecuencia());

            if (vistas.contains(clave)) {
                return clave;
            }

            vistas.add(clave);
        }

        return null;
    }

    private Map<String, List<String>> agruparPlacas(String empresa, String tipoDocumento, BigDecimal numero) {
        Map<String, List<String>> porLinea = new LinkedHashMap<>();

        for (PlacaRequisicionModel placa : placaRepository
                .buscarPorRequisicion(empresa, tipoDocumento, numero)) {

            // ASPLMORS no lleva bodega, asi que la placa se agrupa por articulo
            // y secuencia, que es como el ERP la relaciona con la linea.
            String clave = placa.getArticulo() + "|" + placa.getSecuencia().stripTrailingZeros().toPlainString();
            porLinea.computeIfAbsent(clave, k -> new ArrayList<>()).add(placa.getPlaca());
        }

        return porLinea;
    }

    private RequisicionDetailResponse mapearDetalle(RequisicionCabeceraModel cabecera,
                                                    List<MoviresuEntity> lineas,
                                                    Map<String, List<String>> placas,
                                                    List<RequfirmEntity> firmas) {

        return RequisicionDetailResponse.builder()
                .empresa(cabecera.getEmpresa())
                .tipoDocumento(cabecera.getTipoDocumento())
                .numero(cabecera.getNumero())
                .estado(estadoDerivado(lineas))
                .fecha(cabecera.getFecha())
                .fechaRequerida(cabecera.getFechaRequerida())
                .observacion(cabecera.getObservacion())
                .bodega(cabecera.getBodega())
                .bodegaDestino(cabecera.getBodegaDestino())
                .responsableBodega(cabecera.getResponsableBodega())
                .responsableBodegaDestino(cabecera.getResponsableBodegaDestino())
                .centroInformacion(cabecera.getCentroInformacion())
                .tercero(cabecera.getTercero())
                .lineas(lineas.stream().map(linea -> mapearLinea(linea, placas)).toList())
                .firmas(firmas.stream().map(this::mapearFirma).toList())
                .build();
    }

    private RequisicionLineaResponse mapearLinea(MoviresuEntity linea, Map<String, List<String>> placas) {
        String clave = linea.getMorsarti() + "|" + linea.getMorssecu().stripTrailingZeros().toPlainString();

        return RequisicionLineaResponse.builder()
                .articulo(linea.getMorsarti())
                .bodega(linea.getMorsbode())
                .secuencia(linea.getMorssecu())
                .descripcion(linea.getMorsdear())
                .unidad(linea.getMorsunid())
                .estado(linea.getMorsesta())
                .solicitada(linea.getMorscaso())
                .aprobada(linea.getMorscaap())
                .entregada(linea.getMorscaen())
                .anulada(linea.getMorscaan())
                .recibida(linea.getMorscare())
                .pendiente(linea.getMorscape())
                .usuarioAprueba(linea.getMorsusap())
                .fechaAprobacion(linea.getMorsfeap())
                .placas(placas.getOrDefault(clave, List.of()))
                .build();
    }

    private RequisicionFirmaResponse mapearFirma(RequfirmEntity firma) {
        return RequisicionFirmaResponse.builder()
                .posicion(firma.getRefiposi())
                .tipo(firma.tipoFirma())
                .persona(firma.getReficodi())
                .fechaFirma(firma.getRefifefi())
                .firmada(firma.estaFirmada())
                .firma(firma.estaFirmada() ? Base64.getEncoder().encodeToString(firma.getRefifipe()) : null)
                .build();
    }

    /**
     * Estado del conjunto a partir de las lineas. Misma precedencia que
     * fun_estado en el paquete: manda la fase mas atrasada que quede pendiente.
     *
     * Esta copia sirve para pintar la pantalla. La que AUTORIZA es la del
     * paquete, de modo que si divergieran el peor efecto seria una pantalla
     * optimista y una accion rechazada con mensaje claro, nunca una escritura
     * incorrecta.
     */
    private String estadoDerivado(List<MoviresuEntity> lineas) {
        List<String> precedencia = List.of("in", "ap", "en", "rg");

        for (String estado : precedencia) {
            if (lineas.stream().anyMatch(linea -> estado.equals(linea.getMorsesta()))) {
                return estado;
            }
        }

        return "ae";
    }

    private int posicionDe(String tipoFirma) {
        if (RequfirmEntity.TIPO_SALIDA.equals(tipoFirma)) {
            return RequfirmEntity.POSICION_SALIDA;
        }

        if (RequfirmEntity.TIPO_RECIBE.equals(tipoFirma)) {
            return RequfirmEntity.POSICION_RECIBE;
        }

        throw new BussinessException(ErrorCode.SIGAPP_400,
                "Tipo de firma invalido: " + tipoFirma + ". Se espera SA (salida) o RE (recibe)");
    }

    private String obtenerUsuario(String bearerToken) {
        String token = tokenProvider.extraerToken(bearerToken);
        return tokenProvider.getUsernameFromJWT(token);
    }

    private String claveDe(String articulo, String bodega, BigDecimal secuencia) {
        return articulo + "/" + bodega + "/" + secuencia.stripTrailingZeros().toPlainString();
    }

    private String referencia(String empresa, String tipoDocumento, Long numero) {
        return empresa + "-" + tipoDocumento + "-" + numero;
    }

    private String texto(Object valor) {
        return valor != null ? valor.toString() : null;
    }
}
