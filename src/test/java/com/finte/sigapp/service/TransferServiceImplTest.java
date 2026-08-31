package com.finte.sigapp.service;

import com.finte.sigapp.dto.request.TransferProcessRequest;
import com.finte.sigapp.dto.request.TransferRequest;
import com.finte.sigapp.dto.request.TransferSignRequest;
import com.finte.sigapp.dto.response.ObjectListResponse;
import com.finte.sigapp.dto.response.ObjectResponse;
import com.finte.sigapp.dto.response.TransferCreatedResponse;
import com.finte.sigapp.dto.response.TransferDetailResponse;
import com.finte.sigapp.dto.response.TransferResponse;
import com.finte.sigapp.entity.MovitrasEntity;
import com.finte.sigapp.exception.BussinessException;
import com.finte.sigapp.model.MovitrasTraspasoModel;
import com.finte.sigapp.repository.MovitrasRepository;
import com.finte.sigapp.repository.TransferJpaRepository;
import com.finte.sigapp.repository.TransferRepository;
import com.finte.sigapp.security.JwtTokenProvider;
import com.finte.sigapp.service.impl.TransferServiceImpl;
import com.finte.sigapp.utils.FirmaUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDateTime;
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

    @Mock
    private TransferRepository repository;

    @Mock
    private TransferJpaRepository transferJpaRepository;

    @Mock
    private MovitrasRepository movitrasRepository;

    @Mock
    private JwtTokenProvider tokenProvider;

    /**
     * Se inyecta la implementacion real, no un mock: la validacion de la firma es
     * parte del comportamiento que se quiere verificar en registrarFirma.
     */
    @Spy
    private FirmaUtil firmaUtil = new FirmaUtil(MAX_BYTES_FIRMA);

    @InjectMocks
    private TransferServiceImpl service;

    /** PNG minimo: solo la cabecera, que es lo unico que valida FirmaUtil. */
    private static byte[] pngValido() {
        return new byte[] { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00, 0x00, 0x0D };
    }

    /** El service resuelve el usuario desde el header Authorization. */
    private void stubUsuarioDelToken() {
        when(tokenProvider.extraerToken(BEARER)).thenReturn(TOKEN);
        when(tokenProvider.getUsernameFromJWT(TOKEN)).thenReturn(USUARIO);
    }

    private TransferRequest requestValido() {
        TransferRequest req = new TransferRequest();
        req.setEmpresa("001");
        req.setPersonaFuente("PER_FUENTE");
        req.setPersonaDestino("PER_DESTINO");
        req.setElemento("ART_001");
        req.setPlaca("PLACA_001");
        req.setObservacion("Cambio de responsable");
        return req;
    }

    @Test
    @DisplayName("crearSolicitud devuelve code 0 y la llave del documento generado")
    void crearSolicitud_DevuelveIdGenerado() {
        stubUsuarioDelToken();

        TransferCreatedResponse creado = TransferCreatedResponse.builder()
                .id(100L)
                .empresaDocumento("001")
                .tipoDocumento("RQTR")
                .numeroDocumento(new BigDecimal("4501"))
                .build();

        when(repository.crearSolicitud(eq("001"), eq("PER_FUENTE"), eq("PER_DESTINO"), eq("ART_001"),
                eq("PLACA_001"), any(), isNull(), eq(USUARIO)))
                .thenReturn(creado);

        ObjectResponse res = service.crearSolicitud(requestValido(), BEARER);

        assertEquals(CODE_OK, res.getCode());

        TransferCreatedResponse cuerpo = (TransferCreatedResponse) res.getObject();
        assertEquals(100L, cuerpo.getId());
        assertEquals("RQTR", cuerpo.getTipoDocumento());
        assertEquals(new BigDecimal("4501"), cuerpo.getNumeroDocumento());
    }

    @Test
    @DisplayName("crearSolicitud rechaza que fuente y destino sean la misma persona")
    void crearSolicitud_RechazaFuenteIgualDestino() {
        stubUsuarioDelToken();

        TransferRequest req = requestValido();
        req.setPersonaDestino(req.getPersonaFuente());

        ObjectResponse res = service.crearSolicitud(req, BEARER);

        assertEquals(CODE_ERROR, res.getCode());
        assertTrue(res.getMsg().contains("no pueden ser la misma"));
        verify(repository, never()).crearSolicitud(any(), any(), any(), any(), any(), any(), any(), any());
    }

    @Test
    @DisplayName("crearSolicitud devuelve code -1 si el procedimiento no retorna id")
    void crearSolicitud_DevuelveErrorSiProcedimientoNoRetornaId() {
        stubUsuarioDelToken();

        when(repository.crearSolicitud(any(), any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(null);

        ObjectResponse res = service.crearSolicitud(requestValido(), BEARER);

        assertEquals(CODE_ERROR, res.getCode());
        assertTrue(res.getMsg().contains("no retorno ID"));
    }

    @Test
    @DisplayName("listarSolicitudes con 'pr' consulta todos los estados ya procesados")
    void listarSolicitudes_FiltraCorrectamentePorProcesados() {
        MovitrasTraspasoModel model = new MovitrasTraspasoModel();
        model.setMotridmt(10L);
        model.setMotresta("ap");
        model.setMotrobse("Traspaso por cambio de responsable");
        model.setMotrfecr(LocalDateTime.now());

        when(transferJpaRepository.buscarPorEstados(
                argThat(estados -> estados.containsAll(List.of("ap", "na", "af", "ad", "re"))),
                eq("001"), eq("BOD_01")))
                .thenReturn(List.of(model));

        ObjectListResponse res = service.listarSolicitudes("001", "BOD_01", "pr");

        assertEquals(CODE_OK, res.getCode());
        assertEquals(1, res.getList().size());

        TransferResponse fila = (TransferResponse) res.getList().get(0);
        assertEquals(10L, fila.getId());
        assertEquals("ap", fila.getEstado());
        assertEquals("Traspaso por cambio de responsable", fila.getObservacion());
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

    @Test
    @DisplayName("obtenerDetalle devuelve las firmas del traspaso")
    void obtenerDetalle_DevuelveFirmas() {
        MovitrasEntity entity = new MovitrasEntity();
        entity.setMotridmt(77L);
        entity.setMotresta("ad");
        entity.setMotrfifu(pngValido());
        entity.setMotrfide(null);

        when(movitrasRepository.findById(77L)).thenReturn(Optional.of(entity));

        ObjectResponse res = service.obtenerDetalle(77L);

        assertEquals(CODE_OK, res.getCode());
        TransferDetailResponse detalle = (TransferDetailResponse) res.getObject();
        assertEquals(77L, detalle.getId());
        // Se guarda en BLOB pero el detalle lo expone en base64.
        assertEquals(Base64.getEncoder().encodeToString(pngValido()), detalle.getFirmaFuente());
        assertNull(detalle.getFirmaDestino(), "Sin firmar, la firma viaja como null, no como cadena vacia");
    }

    @Test
    @DisplayName("obtenerDetalle devuelve code -1 cuando el traspaso no existe")
    void obtenerDetalle_DevuelveErrorSiNoExiste() {
        when(movitrasRepository.findById(999L)).thenReturn(Optional.empty());

        ObjectResponse res = service.obtenerDetalle(999L);

        assertEquals(CODE_ERROR, res.getCode());
        assertTrue(res.getMsg().contains("999"));
    }

    @Test
    @DisplayName("procesarTraspaso exige observacion al rechazar")
    void procesarTraspaso_ExigeObservacionAlRechazar() {
        stubUsuarioDelToken();

        TransferProcessRequest req = new TransferProcessRequest();
        req.setEstado("na");
        req.setObservacion("   ");

        ObjectResponse res = service.procesarTraspaso(1L, req, BEARER);

        assertEquals(CODE_ERROR, res.getCode());
        assertTrue(res.getMsg().contains("observacion"));
        verify(repository, never()).aprobarRechazar(anyLong(), any(), any(), any());
    }

    @Test
    @DisplayName("procesarTraspaso delega la aprobacion en el paquete PL/SQL")
    void procesarTraspaso_LlamaProcedimientoExitosamente() {
        stubUsuarioDelToken();

        TransferProcessRequest req = new TransferProcessRequest();
        req.setEstado("ap");
        req.setObservacion("Aprobado según revisión");

        ObjectResponse res = service.procesarTraspaso(1L, req, BEARER);

        assertEquals(CODE_OK, res.getCode());
        verify(repository, times(1)).aprobarRechazar(1L, "ap", "Aprobado según revisión", USUARIO);
    }

    private TransferSignRequest firmaRequest(String base64) {
        TransferSignRequest req = new TransferSignRequest();
        req.setTipoFirma("FU");
        req.setFirma(base64);
        return req;
    }

    @Test
    @DisplayName("registrarFirma decodifica el base64 y entrega los bytes al paquete PL/SQL")
    void registrarFirma_DelegaEnRepositorio() {
        stubUsuarioDelToken();

        String base64 = Base64.getEncoder().encodeToString(pngValido());

        ObjectResponse res = service.registrarFirma(5L, firmaRequest(base64), BEARER);

        assertEquals(CODE_OK, res.getCode());
        verify(repository, times(1)).registrarFirma(5L, "FU", pngValido(), USUARIO);
    }

    @Test
    @DisplayName("registrarFirma acepta el prefijo data URL que produce el canvas del front")
    void registrarFirma_AceptaPrefijoDataUrl() {
        stubUsuarioDelToken();

        String base64 = "data:image/png;base64," + Base64.getEncoder().encodeToString(pngValido());

        ObjectResponse res = service.registrarFirma(5L, firmaRequest(base64), BEARER);

        assertEquals(CODE_OK, res.getCode());
        // El prefijo no llega a la BD: se guardan solo los bytes de la imagen.
        verify(repository, times(1)).registrarFirma(5L, "FU", pngValido(), USUARIO);
    }

    @Test
    @DisplayName("registrarFirma rechaza un base64 invalido sin llamar al paquete")
    void registrarFirma_RechazaBase64Invalido() {
        ObjectResponse res = service.registrarFirma(5L, firmaRequest("no-es-base64-***"), BEARER);

        assertEquals(CODE_ERROR, res.getCode());
        assertTrue(res.getMsg().contains("base64"));
        verify(repository, never()).registrarFirma(anyLong(), any(), any(), any());
    }

    @Test
    @DisplayName("registrarFirma rechaza un base64 que no es una imagen PNG ni JPEG")
    void registrarFirma_RechazaContenidoQueNoEsImagen() {
        String base64 = Base64.getEncoder().encodeToString("esto no es una imagen".getBytes());

        ObjectResponse res = service.registrarFirma(5L, firmaRequest(base64), BEARER);

        assertEquals(CODE_ERROR, res.getCode());
        assertTrue(res.getMsg().contains("PNG"));
        verify(repository, never()).registrarFirma(anyLong(), any(), any(), any());
    }

    @Test
    @DisplayName("obtenerFirma devuelve los bytes de la parte solicitada")
    void obtenerFirma_DevuelveLosBytesDeLaParte() {
        MovitrasEntity entity = new MovitrasEntity();
        entity.setMotridmt(5L);
        entity.setMotrfifu(pngValido());

        when(movitrasRepository.findById(5L)).thenReturn(Optional.of(entity));

        assertArrayEquals(pngValido(), service.obtenerFirma(5L, "fu"));
        assertNull(service.obtenerFirma(5L, "DE"), "El destino aun no ha firmado");
    }

    @Test
    @DisplayName("obtenerFirma devuelve null cuando el traspaso no existe")
    void obtenerFirma_DevuelveNullSiNoExisteElTraspaso() {
        when(movitrasRepository.findById(999L)).thenReturn(Optional.empty());

        assertNull(service.obtenerFirma(999L, "FU"));
    }

    @Test
    @DisplayName("obtenerFirma rechaza un tipo de firma distinto de FU o DE")
    void obtenerFirma_RechazaTipoInvalido() {
        assertThrows(BussinessException.class, () -> service.obtenerFirma(5L, "XX"));
        verify(movitrasRepository, never()).findById(anyLong());
    }

    @Test
    @DisplayName("recibir cierra el traspaso delegando en el paquete PL/SQL")
    void recibir_DelegaEnRepositorio() {
        stubUsuarioDelToken();

        ObjectResponse res = service.recibir(5L, BEARER);

        assertEquals(CODE_OK, res.getCode());
        verify(repository, times(1)).recibir(5L, USUARIO);
    }
}
