package com.finte.sigapp.service;

import com.finte.sigapp.dto.request.RequisicionAnularRequest;
import com.finte.sigapp.dto.request.RequisicionFirmaRequest;
import com.finte.sigapp.dto.request.RequisicionLineaClaveRequest;
import com.finte.sigapp.dto.request.RequisicionLineaRequest;
import com.finte.sigapp.dto.request.RequisicionLineasRequest;
import com.finte.sigapp.dto.request.RequisicionRegistrarRequest;
import com.finte.sigapp.dto.response.ObjectListResponse;
import com.finte.sigapp.dto.response.ObjectResponse;
import com.finte.sigapp.entity.RequfirmEntity;
import com.finte.sigapp.exception.BussinessException;
import com.finte.sigapp.exception.catalog.ErrorCode;
import com.finte.sigapp.model.PlacaRequisicionModel;
import com.finte.sigapp.repository.MoviresuRepository;
import com.finte.sigapp.repository.PlacaRequisicionRepository;
import com.finte.sigapp.repository.RequfirmRepository;
import com.finte.sigapp.repository.RequisicionBandejaRepository;
import com.finte.sigapp.repository.RequisicionCabeceraRepository;
import com.finte.sigapp.repository.RequisicionRepository;
import com.finte.sigapp.repository.TipoRequisicionRepository;
import com.finte.sigapp.security.JwtTokenProvider;
import com.finte.sigapp.service.impl.RequisicionServiceImpl;
import com.finte.sigapp.utils.FirmaUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
@DisplayName("RequisicionServiceImpl")
class RequisicionServiceImplTest {

    private static final int CODE_OK = 0;
    private static final int CODE_ERROR = -1;
    private static final String BEARER = "Bearer token-jwt";
    private static final String USUARIO = "1712345678";
    private static final String EMPRESA = "01";
    private static final String TIPO = "199";
    private static final Long NUMERO = 48213L;
    private static final int MAX_LINEAS = 200;

    @Mock private RequisicionRepository requisicionRepository;
    @Mock private RequisicionBandejaRepository bandejaRepository;
    @Mock private RequisicionCabeceraRepository cabeceraRepository;
    @Mock private MoviresuRepository moviresuRepository;
    @Mock private RequfirmRepository requfirmRepository;
    @Mock private PlacaRequisicionRepository placaRepository;
    @Mock private TipoRequisicionRepository tipoRepository;
    @Mock private JwtTokenProvider tokenProvider;
    @Mock private FirmaUtil firmaUtil;

    private RequisicionServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new RequisicionServiceImpl(requisicionRepository, bandejaRepository, cabeceraRepository,
                moviresuRepository, requfirmRepository, placaRepository, tipoRepository,
                tokenProvider, firmaUtil, MAX_LINEAS, 90, 500);

        when(tokenProvider.extraerToken(BEARER)).thenReturn("token-jwt");
        when(tokenProvider.getUsernameFromJWT("token-jwt")).thenReturn(USUARIO);
    }

    /**
     * El test mas importante del modulo.
     *
     * El ERP identifica al usuario con una variable de sesion Oracle. Si no se
     * fija antes de escribir, el control de responsable de bodega no encuentra a
     * nadie y un NVL del trigger lo da por bueno: la operacion pasa sin
     * autorizacion y sin ningun error visible. Por eso no basta con que se
     * llame, tiene que llamarse ANTES.
     */
    @Test
    @DisplayName("fija el contexto de usuario en Oracle antes de escribir")
    void fijaContextoAntesDeAprobar() {
        service.aprobar(EMPRESA, TIPO, NUMERO, unaLinea(BigDecimal.TEN), BEARER);

        InOrder orden = inOrder(requisicionRepository);
        orden.verify(requisicionRepository).fijarContexto(USUARIO);
        orden.verify(requisicionRepository).aprobarLinea(eq(EMPRESA), eq(TIPO), eq(NUMERO),
                anyString(), anyString(), any(), any(), eq(USUARIO));
    }

    @Test
    @DisplayName("fija el contexto antes de entregar, de anular y de registrar")
    void fijaContextoEnTodaEscritura() {
        service.entregar(EMPRESA, TIPO, NUMERO, unaLinea(BigDecimal.TEN), BEARER);
        service.anular(EMPRESA, TIPO, NUMERO, unaAnulacion(), BEARER);
        service.registrarSalida(EMPRESA, TIPO, NUMERO, new RequisicionRegistrarRequest(List.of()), BEARER);

        verify(requisicionRepository, org.mockito.Mockito.times(3)).fijarContexto(USUARIO);
    }

    @Test
    @DisplayName("rechaza la peticion con la misma linea repetida")
    void rechazaLineaRepetida() {
        RequisicionLineaRequest linea = new RequisicionLineaRequest(
                "SUM0001", "0101", BigDecimal.ONE, BigDecimal.TEN, null);
        RequisicionLineasRequest request = new RequisicionLineasRequest(List.of(linea, linea));

        ObjectResponse response = service.aprobar(EMPRESA, TIPO, NUMERO, request, BEARER);

        assertThat(response.getCode()).isEqualTo(CODE_ERROR);
        assertThat(response.getMsg()).contains("repetida");
        verify(requisicionRepository, never()).aprobarLinea(anyString(), anyString(), any(),
                anyString(), anyString(), any(), any(), anyString());
    }

    @Test
    @DisplayName("rechaza mas lineas de las permitidas sin tocar la base")
    void rechazaExcesoDeLineas() {
        List<RequisicionLineaRequest> muchas = new java.util.ArrayList<>();

        for (int i = 0; i <= MAX_LINEAS; i++) {
            muchas.add(new RequisicionLineaRequest("SUM" + i, "0101", BigDecimal.valueOf(i), BigDecimal.ONE, null));
        }

        ObjectResponse response = service.aprobar(EMPRESA, TIPO, NUMERO, new RequisicionLineasRequest(muchas), BEARER);

        assertThat(response.getCode()).isEqualTo(CODE_ERROR);
        verify(requisicionRepository, never()).fijarContexto(anyString());
    }

    /**
     * El ERP valida el numero de placas en el mismo UPDATE que la entrega, asi
     * que asignarlas despues llegaria tarde.
     */
    @Test
    @DisplayName("asigna las placas antes de entregar la linea")
    void asignaPlacasAntesDeEntregar() {
        RequisicionLineaRequest linea = new RequisicionLineaRequest(
                "ACT0001", "0101", BigDecimal.ONE, BigDecimal.valueOf(2), List.of("PL-1", "PL-2"));

        service.entregar(EMPRESA, TIPO, NUMERO, new RequisicionLineasRequest(List.of(linea)), BEARER);

        InOrder orden = inOrder(requisicionRepository);
        orden.verify(requisicionRepository).asignarPlaca(EMPRESA, TIPO, NUMERO, "ACT0001", BigDecimal.ONE, "PL-1");
        orden.verify(requisicionRepository).asignarPlaca(EMPRESA, TIPO, NUMERO, "ACT0001", BigDecimal.ONE, "PL-2");
        orden.verify(requisicionRepository).entregarLinea(eq(EMPRESA), eq(TIPO), eq(NUMERO),
                eq("ACT0001"), eq("0101"), eq(BigDecimal.ONE), eq(BigDecimal.valueOf(2)), eq(USUARIO));
    }

    /**
     * Sin placas en el payload, Java NO debe inventarse ninguna: la eleccion la
     * hace el paquete por orden de entrada del activo (FIFO), que es donde estan
     * los datos para decidir. Si esta clase asignara algo por su cuenta, se
     * saltaria ese orden.
     */
    @Test
    @DisplayName("no asigna placas cuando no vienen: el FIFO lo resuelve el paquete")
    void sinPlacasDelegaEnElFifo() {
        service.entregar(EMPRESA, TIPO, NUMERO, unaLinea(BigDecimal.valueOf(3)), BEARER);

        verify(requisicionRepository, never()).asignarPlaca(anyString(), anyString(), any(),
                anyString(), any(), anyString());
        verify(requisicionRepository).entregarLinea(eq(EMPRESA), eq(TIPO), eq(NUMERO),
                eq("SUM0001"), eq("0101"), eq(BigDecimal.ONE), eq(BigDecimal.valueOf(3)), eq(USUARIO));
    }

    @Test
    @DisplayName("rechaza la firma que no pasa la validacion sin escribir nada")
    void rechazaFirmaInvalida() {
        when(firmaUtil.decodificar(anyString()))
                .thenThrow(new BussinessException(ErrorCode.SIGAPP_400, "La firma excede el tamano maximo"));

        RequisicionFirmaRequest request = new RequisicionFirmaRequest("SA", USUARIO, "no-es-base64");

        ObjectResponse response = service.registrarFirma(EMPRESA, TIPO, NUMERO, request, BEARER);

        assertThat(response.getCode()).isEqualTo(CODE_ERROR);
        assertThat(response.getMsg()).contains("tamano maximo");
        verify(requisicionRepository, never()).fijarContexto(anyString());
        verify(requisicionRepository, never()).registrarFirma(anyString(), anyString(), any(),
                anyString(), anyString(), any(), anyString());
    }

    @Test
    @DisplayName("registra la firma decodificada")
    void registraFirma() {
        byte[] bytes = {1, 2, 3};
        when(firmaUtil.decodificar("base64")).thenReturn(bytes);

        RequisicionFirmaRequest request = new RequisicionFirmaRequest("RE", USUARIO, "base64");

        ObjectResponse response = service.registrarFirma(EMPRESA, TIPO, NUMERO, request, BEARER);

        assertThat(response.getCode()).isEqualTo(CODE_OK);
        verify(requisicionRepository).registrarFirma(EMPRESA, TIPO, NUMERO, "RE", USUARIO, bytes, USUARIO);
    }

    /**
     * En una recepcion parcial el ERP compara el numero de placas con la
     * cantidad recibida. El front manda las que SI se reciben; las que sobran
     * hay que soltarlas o el registro falla.
     */
    @Test
    @DisplayName("suelta las placas que no se reciben en una recepcion parcial")
    void sueltaPlacasNoRecibidas() {
        when(placaRepository.buscarPorRequisicion(EMPRESA, TIPO, BigDecimal.valueOf(NUMERO)))
                .thenReturn(List.of(
                        placa("ACT0001", BigDecimal.ONE, "PL-1"),
                        placa("ACT0001", BigDecimal.ONE, "PL-2"),
                        placa("ACT0001", BigDecimal.ONE, "PL-3")));

        when(requisicionRepository.generarSalida(EMPRESA, TIPO, NUMERO, USUARIO))
                .thenReturn(Map.of(RequisicionRepository.SALIDA_TIPO_DOCUMENTO, "200",
                        RequisicionRepository.SALIDA_MENSAJE, "ok"));

        RequisicionLineaRequest recibida = new RequisicionLineaRequest(
                "ACT0001", "0101", BigDecimal.ONE, BigDecimal.valueOf(2), List.of("PL-1", "PL-3"));

        ObjectResponse response = service.registrarSalida(EMPRESA, TIPO, NUMERO,
                new RequisicionRegistrarRequest(List.of(recibida)), BEARER);

        assertThat(response.getCode()).isEqualTo(CODE_OK);
        verify(requisicionRepository).soltarPlaca(EMPRESA, TIPO, NUMERO, "ACT0001", BigDecimal.ONE, "PL-2");
        verify(requisicionRepository, never()).soltarPlaca(EMPRESA, TIPO, NUMERO, "ACT0001", BigDecimal.ONE, "PL-1");
        verify(requisicionRepository, never()).soltarPlaca(EMPRESA, TIPO, NUMERO, "ACT0001", BigDecimal.ONE, "PL-3");
    }

    @Test
    @DisplayName("registra la salida sin cuerpo: se recibe todo lo entregado")
    void registraSalidaCompleta() {
        when(requisicionRepository.generarSalida(EMPRESA, TIPO, NUMERO, USUARIO))
                .thenReturn(Map.of(RequisicionRepository.SALIDA_TIPO_DOCUMENTO, "200",
                        RequisicionRepository.SALIDA_MENSAJE, "generado"));

        ObjectResponse response = service.registrarSalida(EMPRESA, TIPO, NUMERO, null, BEARER);

        assertThat(response.getCode()).isEqualTo(CODE_OK);
        verify(requisicionRepository, never()).recibirLinea(anyString(), anyString(), any(),
                anyString(), anyString(), any(), any(), anyString());
        verify(requisicionRepository).generarSalida(EMPRESA, TIPO, NUMERO, USUARIO);
    }

    @Test
    @DisplayName("avisa cuando no hay tipos gestionables")
    void listaTiposVacia() {
        when(tipoRepository.buscarGestionables()).thenReturn(List.of());

        ObjectListResponse response = service.listarTipos();

        assertThat(response.getCode()).isEqualTo(CODE_OK);
        assertThat(response.getList()).isEmpty();
    }

    @Test
    @DisplayName("rechaza el detalle de una requisicion que no existe")
    void detalleInexistente() {
        when(cabeceraRepository.buscarCabecera(EMPRESA, TIPO, BigDecimal.valueOf(NUMERO)))
                .thenReturn(Optional.empty());

        ObjectResponse response = service.obtenerDetalle(EMPRESA, TIPO, NUMERO);

        assertThat(response.getCode()).isEqualTo(CODE_ERROR);
        assertThat(response.getMsg()).contains("No existe");
    }

    @Test
    @DisplayName("rechaza un tipo de firma que no es SA ni RE")
    void tipoDeFirmaInvalido() {
        assertThatThrownBy(() -> service.obtenerFirma(EMPRESA, TIPO, NUMERO, "XX"))
                .isInstanceOf(BussinessException.class)
                .hasMessageContaining("XX");
    }

    @Test
    @DisplayName("devuelve los bytes de una firma registrada")
    void devuelveFirma() {
        byte[] bytes = {9, 8, 7};

        when(requfirmRepository.findByRefiemprAndRefitidoAndRefinumeAndRefiposi(
                EMPRESA, TIPO, BigDecimal.valueOf(NUMERO), RequfirmEntity.POSICION_SALIDA))
                .thenReturn(Optional.of(RequfirmEntity.builder().refifipe(bytes).build()));

        assertThat(service.obtenerFirma(EMPRESA, TIPO, NUMERO, "SA")).isEqualTo(bytes);
    }

    private RequisicionLineasRequest unaLinea(BigDecimal cantidad) {
        return new RequisicionLineasRequest(List.of(
                new RequisicionLineaRequest("SUM0001", "0101", BigDecimal.ONE, cantidad, null)));
    }

    private RequisicionAnularRequest unaAnulacion() {
        return new RequisicionAnularRequest(List.of(
                new RequisicionLineaClaveRequest("SUM0001", "0101", BigDecimal.ONE)));
    }

    private PlacaRequisicionModel placa(String articulo, BigDecimal secuencia, String placa) {
        return PlacaRequisicionModel.builder()
                .empresa(EMPRESA)
                .tipoDocumento(TIPO)
                .numero(BigDecimal.valueOf(NUMERO))
                .secuencia(secuencia)
                .articulo(articulo)
                .placa(placa)
                .build();
    }
}
