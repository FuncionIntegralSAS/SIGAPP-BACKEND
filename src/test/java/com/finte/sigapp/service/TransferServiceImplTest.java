package com.finte.sigapp.service;

import com.finte.sigapp.dto.request.TransferItemRequest;
import com.finte.sigapp.dto.request.TransferProcessRequest;
import com.finte.sigapp.dto.request.TransferRequest;
import com.finte.sigapp.dto.request.TransferSignRequest;
import com.finte.sigapp.dto.response.ObjectListResponse;
import com.finte.sigapp.dto.response.ObjectResponse;
import com.finte.sigapp.dto.response.TransferAssetResponse;
import com.finte.sigapp.dto.response.TransferCreatedResponse;
import com.finte.sigapp.dto.response.TransferDetailResponse;
import com.finte.sigapp.dto.response.TransferItemResponse;
import com.finte.sigapp.dto.response.TransferPersonResponse;
import com.finte.sigapp.dto.response.TransferResponse;
import com.finte.sigapp.dto.response.TransferSignatureResponse;
import com.finte.sigapp.entity.MotrfirmEntity;
import com.finte.sigapp.entity.MovitrasEntity;
import com.finte.sigapp.exception.BussinessException;
import com.finte.sigapp.model.ActivoCompatibilidadModel;
import com.finte.sigapp.model.ActivoPersonaModel;
import com.finte.sigapp.model.MovitrasTraspasoModel;
import com.finte.sigapp.model.PersonalModel;
import com.finte.sigapp.repository.ActivoCompatibilidadRepository;
import com.finte.sigapp.repository.ActivoPersonaRepository;
import com.finte.sigapp.repository.MotrfirmRepository;
import com.finte.sigapp.repository.MovitrasRepository;
import com.finte.sigapp.repository.PersonalRepository;
import com.finte.sigapp.repository.TransferJpaRepository;
import com.finte.sigapp.repository.TransferRepository;
import com.finte.sigapp.security.JwtTokenProvider;
import com.finte.sigapp.service.impl.TransferServiceImpl;
import com.finte.sigapp.utils.FirmaUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class TransferServiceImplTest {

    private static final int CODE_OK = 0;
    private static final int CODE_ERROR = -1;

    private static final String BEARER = "Bearer token-jwt";
    private static final String TOKEN = "token-jwt";
    private static final String USUARIO = "USER_01";

    private static final int MAX_BYTES_FIRMA = 512_000;
    private static final int MAX_ARTICULOS = 50;

    /** Numero de tramite: el identificador con el que opera todo el modulo. */
    private static final Long TRAMITE = 8801L;

    @Mock
    private TransferRepository repository;

    @Mock
    private TransferJpaRepository transferJpaRepository;

    @Mock
    private MovitrasRepository movitrasRepository;

    @Mock
    private MotrfirmRepository motrfirmRepository;

    @Mock
    private ActivoPersonaRepository activoPersonaRepository;

    @Mock
    private ActivoCompatibilidadRepository activoCompatibilidadRepository;

    @Mock
    private PersonalRepository personalRepository;

    @Mock
    private JwtTokenProvider tokenProvider;

    private TransferServiceImpl service;

    /**
     * El service recibe el tope por constructor, asi que se arma a mano en vez de
     * con @InjectMocks. FirmaUtil va real: su validacion es parte del
     * comportamiento que se quiere verificar en registrarFirma.
     */
    @BeforeEach
    void setUp() {
        service = new TransferServiceImpl(repository,
                transferJpaRepository,
                movitrasRepository,
                motrfirmRepository,
                activoPersonaRepository,
                activoCompatibilidadRepository,
                personalRepository,
                tokenProvider,
                new FirmaUtil(MAX_BYTES_FIRMA),
                MAX_ARTICULOS);
    }

    /** El service resuelve el usuario desde el header Authorization. */
    private void stubUsuarioDelToken() {
        when(tokenProvider.extraerToken(BEARER)).thenReturn(TOKEN);
        when(tokenProvider.getUsernameFromJWT(TOKEN)).thenReturn(USUARIO);
    }

    /** PNG minimo: solo la cabecera, que es lo unico que valida FirmaUtil. */
    private static byte[] pngValido() {
        return new byte[] { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00, 0x00, 0x0D };
    }

    private static TransferItemRequest articulo(String codigo, String placa) {
        return new TransferItemRequest(codigo, placa);
    }

    private TransferRequest requestValido(TransferItemRequest... articulos) {
        TransferRequest req = new TransferRequest();
        req.setEmpresa("001");
        req.setPersonaFuente("PER_FUENTE");
        req.setPersonaDestino("PER_DESTINO");
        req.setObservacion("Cambio de responsable");
        req.setArticulos(new ArrayList<>(List.of(articulos)));
        return req;
    }

    private TransferCreatedResponse creado() {
        return TransferCreatedResponse.builder()
                .id(TRAMITE)
                .empresaDocumento("001")
                .tipoDocumento("RQTR")
                .numeroDocumento(new BigDecimal("4501"))
                .build();
    }

    private void stubCreacionOk() {
        when(repository.crearSolicitud(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(creado());
        stubSinComprometidos();
    }

    /** Ningun articulo de la solicitud esta en un traspaso abierto. */
    private void stubSinComprometidos() {
        when(movitrasRepository.findByMotrestaInAndMotrartiIn(anyCollection(), anyCollection()))
                .thenReturn(List.of());
    }

    /** Linea de un traspaso abierto que ya usa ese elemento. */
    private MovitrasEntity lineaAbierta(Long tramite, String articulo, String placa) {
        MovitrasEntity e = new MovitrasEntity();
        e.setMotrnutr(tramite);
        e.setMotrarti(articulo);
        e.setMotrplac(placa);
        e.setMotresta("pe");
        return e;
    }

    // ------------------------------------------------------------------ crear

    @Test
    @DisplayName("crearSolicitud abre el tramite con el primer articulo y agrega el resto")
    void crearSolicitud_AgregaLosArticulosRestantes() {
        stubUsuarioDelToken();
        stubCreacionOk();

        TransferRequest req = requestValido(
                articulo("ART-0012", "PL-771"),
                articulo("ART-0034", "PL-802"),
                articulo("ART-0090", null));

        ObjectResponse res = service.crearSolicitud(req, BEARER);

        assertEquals(CODE_OK, res.getCode());
        assertEquals(TRAMITE, ((TransferCreatedResponse) res.getObject()).getId());

        // El primero abre el tramite: va en crearSolicitud, no en agregarArticulo.
        verify(repository).crearSolicitud(eq("001"), eq("PER_FUENTE"), eq("PER_DESTINO"),
                eq("ART-0012"), eq("PL-771"), eq("Cambio de responsable"), isNull(), eq(USUARIO));

        verify(repository).agregarArticulo(TRAMITE, "ART-0034", "PL-802", USUARIO);
        // Placa nula normalizada a punto, igual que ACTIFIJO.
        verify(repository).agregarArticulo(TRAMITE, "ART-0090", ".", USUARIO);
        verify(repository, times(2)).agregarArticulo(anyLong(), any(), any(), any());
    }

    /** Activo con el dato que obliga a que el traspaso sea homogeneo. */
    private ActivoCompatibilidadModel activoCon(String codigo, String placa, String centroInformacion) {
        return ActivoCompatibilidadModel.builder()
                .acfiarti(codigo)
                .acfiplac(placa)
                .acfiobse("Elemento " + codigo)
                .acficein(centroInformacion)
                .build();
    }

    /**
     * Lo que se comprueba no es solo que rechace, sino que lo haga ANTES de
     * crear nada: si el documento llegara a abrirse, el paquete cortaria en el
     * segundo articulo con un ORA-20008 lleno de codigos crudos.
     */
    @Test
    @DisplayName("crearSolicitud rechaza mezclar centros de informacion antes de abrir el documento")
    void crearSolicitud_RechazaMezclaDeCentrosDeInformacion() {
        stubUsuarioDelToken();
        stubSinComprometidos();

        when(activoCompatibilidadRepository.buscarDatosDeCabecera(anyCollection(), eq("001")))
                .thenReturn(List.of(
                        activoCon("ART-0012", "PL-771", "01"),
                        activoCon("ART-0034", "PL-802", "02")));

        ObjectResponse res = service.crearSolicitud(
                requestValido(articulo("ART-0012", "PL-771"), articulo("ART-0034", "PL-802")), BEARER);

        assertEquals(CODE_ERROR, res.getCode());
        assertTrue(res.getMsg().contains("centro de informacion"), res.getMsg());

        verify(repository, never()).crearSolicitud(any(), any(), any(), any(), any(), any(), any(), any());
        verify(repository, never()).agregarArticulo(anyLong(), any(), any(), any());
    }

    /**
     * El caso que reporto el error en produccion: dos activos comprados a
     * proveedores distintos. ACFITERC es el proveedor, no el responsable, asi
     * que no tiene nada que ver con un cambio de responsable interno y el
     * traspaso debe pasar.
     */
    @Test
    @DisplayName("crearSolicitud acepta elementos de distinto proveedor si comparten centro")
    void crearSolicitud_AceptaElementosCompatibles() {
        stubUsuarioDelToken();
        stubCreacionOk();

        when(activoCompatibilidadRepository.buscarDatosDeCabecera(anyCollection(), eq("001")))
                .thenReturn(List.of(
                        activoCon("ART-0012", "PL-771", "01"),
                        activoCon("ART-0034", "PL-802", "01")));

        ObjectResponse res = service.crearSolicitud(
                requestValido(articulo("ART-0012", "PL-771"), articulo("ART-0034", "PL-802")), BEARER);

        assertEquals(CODE_OK, res.getCode());
        verify(repository).agregarArticulo(TRAMITE, "ART-0034", "PL-802", USUARIO);
    }

    /**
     * Un elemento que no esta en ACTIFIJO no se puede clasificar aqui. No se
     * inventa un rechazo: de ese caso se encarga el paquete, que es quien sabe
     * decir que el activo no existe.
     */
    @Test
    @DisplayName("crearSolicitud no rechaza cuando el activo no aparece en ACTIFIJO")
    void crearSolicitud_SinDatosDeActivoNoRechaza() {
        stubUsuarioDelToken();
        stubCreacionOk();

        when(activoCompatibilidadRepository.buscarDatosDeCabecera(anyCollection(), eq("001")))
                .thenReturn(List.of());

        ObjectResponse res = service.crearSolicitud(
                requestValido(articulo("ART-0012", "PL-771"), articulo("ART-0034", "PL-802")), BEARER);

        assertEquals(CODE_OK, res.getCode());
    }

    @Test
    @DisplayName("crearSolicitud con un solo articulo no llama a agregarArticulo")
    void crearSolicitud_UnSoloArticulo() {
        stubUsuarioDelToken();
        stubCreacionOk();

        ObjectResponse res = service.crearSolicitud(requestValido(articulo("ART-0012", "PL-771")), BEARER);

        assertEquals(CODE_OK, res.getCode());
        verify(repository, never()).agregarArticulo(anyLong(), any(), any(), any());
    }

    @Test
    @DisplayName("crearSolicitud rechaza el mismo activo repetido sin abrir documento")
    void crearSolicitud_RechazaArticuloRepetido() {
        stubUsuarioDelToken();

        TransferRequest req = requestValido(
                articulo("ART-0034", "PL-802"),
                articulo("ART-0034", "PL-802"));

        ObjectResponse res = service.crearSolicitud(req, BEARER);

        assertEquals(CODE_ERROR, res.getCode());
        assertTrue(res.getMsg().contains("repetido"));
        verify(repository, never()).crearSolicitud(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("crearSolicitud acepta el mismo articulo con placas distintas: son dos elementos")
    void crearSolicitud_AceptaMismoArticuloConPlacasDistintas() {
        stubUsuarioDelToken();
        stubCreacionOk();

        TransferRequest req = requestValido(
                articulo("ART-0034", "PL-802"),
                articulo("ART-0034", "PL-803"));

        assertEquals(CODE_OK, service.crearSolicitud(req, BEARER).getCode());
        verify(repository).agregarArticulo(TRAMITE, "ART-0034", "PL-803", USUARIO);
    }

    @Test
    @DisplayName("crearSolicitud rechaza superar el tope de articulos")
    void crearSolicitud_RechazaSuperarElTope() {
        stubUsuarioDelToken();

        TransferItemRequest[] muchos = new TransferItemRequest[MAX_ARTICULOS + 1];
        for (int i = 0; i < muchos.length; i++) {
            muchos[i] = articulo("ART-" + i, "PL-" + i);
        }

        ObjectResponse res = service.crearSolicitud(requestValido(muchos), BEARER);

        assertEquals(CODE_ERROR, res.getCode());
        assertTrue(res.getMsg().contains(String.valueOf(MAX_ARTICULOS)));
        verify(repository, never()).crearSolicitud(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("crearSolicitud rechaza los articulos que ya estan en un traspaso abierto")
    void crearSolicitud_RechazaArticuloComprometido() {
        stubUsuarioDelToken();

        when(movitrasRepository.findByMotrestaInAndMotrartiIn(anyCollection(), anyCollection()))
                .thenReturn(List.of(lineaAbierta(7L, "ART-0034", "PL-802")));

        TransferRequest req = requestValido(
                articulo("ART-0012", "PL-771"),
                articulo("ART-0034", "PL-802"));

        ObjectResponse res = service.crearSolicitud(req, BEARER);

        assertEquals(CODE_ERROR, res.getCode());
        assertTrue(res.getMsg().contains("ART-0034"), "El mensaje nombra el articulo");
        assertTrue(res.getMsg().contains("7"), "y el traspaso que lo tiene");
        // Nada se crea: ni documento ni lineas.
        verify(repository, never()).crearSolicitud(any(), any(), any(), any(), any(), any(), any(), any());
        verify(repository, never()).agregarArticulo(anyLong(), any(), any(), any());
    }

    @Test
    @DisplayName("crearSolicitud lista TODOS los comprometidos en un solo mensaje")
    void crearSolicitud_ListaTodosLosComprometidos() {
        stubUsuarioDelToken();

        when(movitrasRepository.findByMotrestaInAndMotrartiIn(anyCollection(), anyCollection()))
                .thenReturn(List.of(lineaAbierta(7L, "ART-0034", "PL-802"),
                                    lineaAbierta(9L, "ART-0090", ".")));

        TransferRequest req = requestValido(
                articulo("ART-0012", "PL-771"),
                articulo("ART-0034", "PL-802"),
                articulo("ART-0090", null));

        ObjectResponse res = service.crearSolicitud(req, BEARER);

        assertEquals(CODE_ERROR, res.getCode());
        // El paquete cortaria en el primero; aqui salen los dos de una vez.
        assertTrue(res.getMsg().contains("ART-0034"));
        assertTrue(res.getMsg().contains("ART-0090"));
        assertFalse(res.getMsg().contains("ART-0012"), "El que esta libre no se menciona");
    }

    @Test
    @DisplayName("crearSolicitud no bloquea el mismo articulo con otra placa")
    void crearSolicitud_OtraPlacaEsOtroElemento() {
        stubUsuarioDelToken();
        stubCreacionOk();

        // El traspaso 7 tiene ART-0034 con la placa 802; se pide la 803.
        when(movitrasRepository.findByMotrestaInAndMotrartiIn(anyCollection(), anyCollection()))
                .thenReturn(List.of(lineaAbierta(7L, "ART-0034", "PL-802")));

        ObjectResponse res = service.crearSolicitud(requestValido(articulo("ART-0034", "PL-803")), BEARER);

        assertEquals(CODE_OK, res.getCode());
    }

    @Test
    @DisplayName("crearSolicitud rechaza que fuente y destino sean la misma persona")
    void crearSolicitud_RechazaFuenteIgualDestino() {
        stubUsuarioDelToken();

        TransferRequest req = requestValido(articulo("ART-0012", "PL-771"));
        req.setPersonaDestino(req.getPersonaFuente());

        ObjectResponse res = service.crearSolicitud(req, BEARER);

        assertEquals(CODE_ERROR, res.getCode());
        assertTrue(res.getMsg().contains("no pueden ser la misma"));
        verify(repository, never()).crearSolicitud(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("crearSolicitud devuelve code -1 si el procedimiento no retorna el numero de tramite")
    void crearSolicitud_DevuelveErrorSiNoRetornaNumero() {
        stubUsuarioDelToken();

        stubSinComprometidos();
        when(repository.crearSolicitud(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(null);

        ObjectResponse res = service.crearSolicitud(requestValido(articulo("ART-0012", "PL-771")), BEARER);

        assertEquals(CODE_ERROR, res.getCode());
        assertTrue(res.getMsg().contains("numero de tramite"));
        verify(repository, never()).agregarArticulo(anyLong(), any(), any(), any());
    }

    // ---------------------------------------------------------------- bandeja

    @Test
    @DisplayName("listarSolicitudes con 'pr' consulta ap, na y re, y devuelve solo la referencia del tramite")
    void listarSolicitudes_ProcesadosSonTresEstados() {
        MovitrasTraspasoModel model = new MovitrasTraspasoModel();
        model.setMotrnutr(TRAMITE);
        model.setMotrdrtd("RQTR");
        model.setMotrdrnd(new BigDecimal("4501"));
        model.setMotrfecr(LocalDateTime.now());

        when(transferJpaRepository.buscarPorEstados(
                argThat(estados -> estados.size() == 3 && estados.containsAll(List.of("ap", "na", "re"))),
                eq("001"), eq("BOD_01")))
                .thenReturn(List.of(model));

        ObjectListResponse res = service.listarSolicitudes("001", "BOD_01", "pr");

        assertEquals(CODE_OK, res.getCode());
        assertEquals(1, res.getList().size());

        TransferResponse fila = (TransferResponse) res.getList().get(0);
        // El id de la bandeja es el numero de TRAMITE (MOTRNUTR), que es lo que va
        // en {id} del resto de rutas. numeroDocumento es otra secuencia: el
        // RESUNUME de la requisicion en el ERP.
        assertEquals(TRAMITE, fila.getId());
        assertEquals("RQTR", fila.getTipoDocumento());
        assertEquals(new BigDecimal("4501"), fila.getNumeroDocumento());
        assertNotNull(fila.getFechaCreacion());
    }

    @Test
    @DisplayName("listarSolicitudes con un estado puntual no lo expande")
    void listarSolicitudes_UsaEstadoSimpleCuandoNoEsProcesados() {
        when(transferJpaRepository.buscarPorEstados(List.of("pe"), null, null)).thenReturn(List.of());

        ObjectListResponse res = service.listarSolicitudes(null, null, "pe");

        assertEquals(CODE_OK, res.getCode());
        assertTrue(res.getList().isEmpty());
        verify(transferJpaRepository, times(1)).buscarPorEstados(List.of("pe"), null, null);
    }

    // ---------------------------------------------------------------- detalle

    private MovitrasEntity linea(Long id, String articulo, String placa) {
        MovitrasEntity e = new MovitrasEntity();
        e.setMotridmt(id);
        e.setMotrnutr(TRAMITE);
        e.setMotresta("ap");
        e.setMotrarti(articulo);
        e.setMotrplac(placa);
        e.setMotrpefu("PER_FUENTE");
        e.setMotrpede("PER_DESTINO");
        return e;
    }

    private MotrfirmEntity firma(int posicion, String persona, byte[] imagen) {
        MotrfirmEntity f = new MotrfirmEntity();
        f.setMofinutr(TRAMITE);
        f.setMofiposi(posicion);
        f.setMoficodi(persona);
        if (imagen != null) {
            f.setMofifipe(imagen);
            f.setMofifefi(LocalDateTime.now());
        }
        return f;
    }

    @Test
    @DisplayName("obtenerDetalle devuelve las N lineas y las dos firmas con su imagen")
    void obtenerDetalle_DevuelveArticulosYFirmas() {
        when(movitrasRepository.findByMotrnutrOrderByMotridmt(TRAMITE))
                .thenReturn(List.of(linea(4501L, "ART-0012", "PL-771"),
                                    linea(4502L, "ART-0090", ".")));

        when(motrfirmRepository.findByMofinutrOrderByMofiposi(TRAMITE))
                .thenReturn(List.of(firma(1, "PER_FUENTE", null),
                                    firma(2, "PER_DESTINO", pngValido())));

        ObjectResponse res = service.obtenerDetalle(TRAMITE);

        assertEquals(CODE_OK, res.getCode());
        TransferDetailResponse detalle = (TransferDetailResponse) res.getObject();

        assertEquals(TRAMITE, detalle.getId());
        assertEquals(2, detalle.getArticulos().size());

        TransferItemResponse primero = detalle.getArticulos().get(0);
        assertEquals("ART-0012", primero.getArticulo());
        assertEquals("PL-771", primero.getPlaca());

        // Todo lo de las firmas vive en firmas[]: la cabecera no repite fechas ni
        // saca las imagenes a campos sueltos.
        assertEquals(2, detalle.getFirmas().size());

        TransferSignatureResponse fuente = detalle.getFirmas().get(0);
        assertEquals(1, fuente.getPosicion());
        assertEquals("FU", fuente.getTipo());
        assertFalse(fuente.isFirmada(), "La fuente aun no ha firmado");
        assertNull(fuente.getFechaFirma());
        assertNull(fuente.getFirma(), "Sin firmar no hay imagen que devolver");

        // El destino firmo primero: no hay orden entre las dos.
        TransferSignatureResponse destino = detalle.getFirmas().get(1);
        assertEquals(2, destino.getPosicion());
        assertEquals("DE", destino.getTipo());
        assertTrue(destino.isFirmada());
        assertNotNull(destino.getFechaFirma());
        // La imagen viaja en base64 para que el front la pinte sin otra peticion.
        assertEquals(Base64.getEncoder().encodeToString(pngValido()), destino.getFirma());
    }

    @Test
    @DisplayName("obtenerDetalle devuelve code -1 cuando el traspaso no existe")
    void obtenerDetalle_DevuelveErrorSiNoExiste() {
        when(movitrasRepository.findByMotrnutrOrderByMotridmt(999L)).thenReturn(List.of());

        ObjectResponse res = service.obtenerDetalle(999L);

        assertEquals(CODE_ERROR, res.getCode());
        assertTrue(res.getMsg().contains("999"));
        verify(motrfirmRepository, never()).findByMofinutrOrderByMofiposi(anyLong());
    }

    // --------------------------------------------------------------- procesar

    @Test
    @DisplayName("procesarTraspaso exige observacion al rechazar")
    void procesarTraspaso_ExigeObservacionAlRechazar() {
        stubUsuarioDelToken();

        TransferProcessRequest req = new TransferProcessRequest();
        req.setEstado("na");
        req.setObservacion("   ");

        ObjectResponse res = service.procesarTraspaso(TRAMITE, req, BEARER);

        assertEquals(CODE_ERROR, res.getCode());
        assertTrue(res.getMsg().contains("observacion"));
        verify(repository, never()).aprobarRechazar(anyLong(), any(), any(), any());
    }

    @Test
    @DisplayName("procesarTraspaso aprueba el tramite completo por su numero")
    void procesarTraspaso_LlamaProcedimientoExitosamente() {
        stubUsuarioDelToken();

        TransferProcessRequest req = new TransferProcessRequest();
        req.setEstado("ap");
        req.setObservacion("Aprobado según revisión");

        ObjectResponse res = service.procesarTraspaso(TRAMITE, req, BEARER);

        assertEquals(CODE_OK, res.getCode());
        verify(repository, times(1)).aprobarRechazar(TRAMITE, "ap", "Aprobado según revisión", USUARIO);
    }

    // ----------------------------------------------------------------- firmas

    private TransferSignRequest firmaRequest(String tipo, String base64) {
        TransferSignRequest req = new TransferSignRequest();
        req.setTipoFirma(tipo);
        req.setFirma(base64);
        return req;
    }

    @Test
    @DisplayName("registrarFirma decodifica el base64 y entrega los bytes al paquete")
    void registrarFirma_DelegaEnRepositorio() {
        stubUsuarioDelToken();

        String base64 = Base64.getEncoder().encodeToString(pngValido());

        ObjectResponse res = service.registrarFirma(TRAMITE, firmaRequest("FU", base64), BEARER);

        assertEquals(CODE_OK, res.getCode());
        verify(repository, times(1)).registrarFirma(TRAMITE, "FU", pngValido(), USUARIO);
    }

    @Test
    @DisplayName("registrarFirma acepta que el destino firme primero: no hay orden")
    void registrarFirma_AceptaDestinoPrimero() {
        stubUsuarioDelToken();

        String base64 = Base64.getEncoder().encodeToString(pngValido());

        ObjectResponse res = service.registrarFirma(TRAMITE, firmaRequest("DE", base64), BEARER);

        assertEquals(CODE_OK, res.getCode());
        verify(repository, times(1)).registrarFirma(TRAMITE, "DE", pngValido(), USUARIO);
    }

    @Test
    @DisplayName("registrarFirma acepta el prefijo data URL que produce el canvas del front")
    void registrarFirma_AceptaPrefijoDataUrl() {
        stubUsuarioDelToken();

        String base64 = "data:image/png;base64," + Base64.getEncoder().encodeToString(pngValido());

        ObjectResponse res = service.registrarFirma(TRAMITE, firmaRequest("FU", base64), BEARER);

        assertEquals(CODE_OK, res.getCode());
        // El prefijo no llega a la BD: se guardan solo los bytes de la imagen.
        verify(repository, times(1)).registrarFirma(TRAMITE, "FU", pngValido(), USUARIO);
    }

    @Test
    @DisplayName("registrarFirma rechaza un base64 invalido sin llamar al paquete")
    void registrarFirma_RechazaBase64Invalido() {
        ObjectResponse res = service.registrarFirma(TRAMITE, firmaRequest("FU", "no-es-base64-***"), BEARER);

        assertEquals(CODE_ERROR, res.getCode());
        assertTrue(res.getMsg().contains("base64"));
        verify(repository, never()).registrarFirma(anyLong(), any(), any(), any());
    }

    @Test
    @DisplayName("registrarFirma rechaza un base64 que no es una imagen PNG ni JPEG")
    void registrarFirma_RechazaContenidoQueNoEsImagen() {
        String base64 = Base64.getEncoder().encodeToString("esto no es una imagen".getBytes());

        ObjectResponse res = service.registrarFirma(TRAMITE, firmaRequest("FU", base64), BEARER);

        assertEquals(CODE_ERROR, res.getCode());
        assertTrue(res.getMsg().contains("PNG"));
        verify(repository, never()).registrarFirma(anyLong(), any(), any(), any());
    }

    @Test
    @DisplayName("obtenerFirma lee la posicion correspondiente al tipo")
    void obtenerFirma_LeeLaPosicionDelTipo() {
        when(motrfirmRepository.findByMofinutrAndMofiposi(TRAMITE, 1))
                .thenReturn(Optional.of(firma(1, "PER_FUENTE", pngValido())));
        when(motrfirmRepository.findByMofinutrAndMofiposi(TRAMITE, 2))
                .thenReturn(Optional.of(firma(2, "PER_DESTINO", null)));

        assertArrayEquals(pngValido(), service.obtenerFirma(TRAMITE, "fu"));
        assertNull(service.obtenerFirma(TRAMITE, "DE"), "El destino aun no ha firmado");
    }

    @Test
    @DisplayName("obtenerFirma devuelve null cuando el traspaso no existe")
    void obtenerFirma_DevuelveNullSiNoExisteElTraspaso() {
        when(motrfirmRepository.findByMofinutrAndMofiposi(999L, 1)).thenReturn(Optional.empty());

        assertNull(service.obtenerFirma(999L, "FU"));
    }

    @Test
    @DisplayName("obtenerFirma rechaza un tipo de firma distinto de FU o DE")
    void obtenerFirma_RechazaTipoInvalido() {
        assertThrows(BussinessException.class, () -> service.obtenerFirma(TRAMITE, "XX"));
        verify(motrfirmRepository, never()).findByMofinutrAndMofiposi(anyLong(), anyInt());
    }

    // --------------------------------------------------------------- recibir

    @Test
    @DisplayName("recibir cierra el traspaso delegando en el paquete, que genera el inventario")
    void recibir_DelegaEnRepositorio() {
        stubUsuarioDelToken();

        ObjectResponse res = service.recibir(TRAMITE, BEARER);

        assertEquals(CODE_OK, res.getCode());
        verify(repository, times(1)).recibir(TRAMITE, USUARIO);
    }

    // ------------------------------------------------- apoyo al formulario

    private ActivoPersonaModel activo(String articulo, String placa, String cein, Integer enTramite) {
        ActivoPersonaModel m = new ActivoPersonaModel();
        m.setAcfiarti(articulo);
        m.setAcfiplac(placa);
        m.setAcfiobse("Portatil Dell");
        m.setAcficein(cein);
        m.setAcfiterc("TER_01");
        m.setEnTramite(enTramite);
        return m;
    }

    @Test
    @DisplayName("listarActivosDePersona marca los que ya estan en un traspaso abierto")
    void listarActivosDePersona_MarcaLosComprometidos() {
        when(activoPersonaRepository.buscarPorResponsable("PER_FUENTE", "B01", "001"))
                .thenReturn(List.of(activo("ART-0012", "PL-771", "CEIN_A", 0),
                                    activo("ART-0034", "PL-802", "CEIN_A", 1)));

        ObjectListResponse res = service.listarActivosDePersona("PER_FUENTE", "B01", "001");

        assertEquals(CODE_OK, res.getCode());
        assertEquals(2, res.getList().size());

        TransferAssetResponse libre = (TransferAssetResponse) res.getList().get(0);
        assertEquals("ART-0012", libre.getArticulo());
        assertEquals("PL-771", libre.getPlaca());
        // Centro de informacion y tercero viajan para que el front no deje mezclar.
        assertEquals("CEIN_A", libre.getCentroInformacion());
        assertEquals("TER_01", libre.getTercero());
        assertFalse(libre.isEnTramite());

        // No se excluye: se marca, para que el usuario sepa por que no puede usarlo.
        assertTrue(((TransferAssetResponse) res.getList().get(1)).isEnTramite());
    }

    @Test
    @DisplayName("listarActivosDePersona trata un ENTRAMITE nulo como no comprometido")
    void listarActivosDePersona_ToleraEnTramiteNulo() {
        when(activoPersonaRepository.buscarPorResponsable("PER_FUENTE", "B01", null))
                .thenReturn(List.of(activo("ART-0012", ".", "CEIN_A", null)));

        ObjectListResponse res = service.listarActivosDePersona("PER_FUENTE", "B01", null);

        assertFalse(((TransferAssetResponse) res.getList().get(0)).isEnTramite());
    }

    @Test
    @DisplayName("listarPersonasDeBodega devuelve la cedula que se envia al crear")
    void listarPersonasDeBodega_DevuelveLaCedula() {
        PersonalModel persona = new PersonalModel();
        persona.setPerscodi("16801364");
        persona.setPersnomb("Ana");
        persona.setPersapel("Gomez");

        when(personalRepository.buscarPorBodega("BOD_01", "001")).thenReturn(List.of(persona));

        ObjectListResponse res = service.listarPersonasDeBodega("BOD_01", "001");

        assertEquals(CODE_OK, res.getCode());
        assertEquals(1, res.getList().size());

        TransferPersonResponse fila = (TransferPersonResponse) res.getList().get(0);
        assertEquals("16801364", fila.getCedula());
        assertEquals("Ana", fila.getNombre());
        assertEquals("Gomez", fila.getApellido());
    }
}
