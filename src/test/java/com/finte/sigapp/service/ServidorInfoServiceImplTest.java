package com.finte.sigapp.service;

import com.finte.sigapp.dto.response.ObjectResponse;
import com.finte.sigapp.dto.response.ServidorInfoResponse;
import com.finte.sigapp.model.ParametroModel;
import com.finte.sigapp.repository.ParametroRepository;
import com.finte.sigapp.service.impl.ServidorInfoServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ServidorInfoServiceImplTest {

    private static final String PARAMETRO_URL = "URLSIGAPP";

    @Mock
    private ParametroRepository parametroRepository;

    private ServidorInfoServiceImpl servidorInfoService;

    @BeforeEach
    void setUp() {
        servidorInfoService = new ServidorInfoServiceImpl(parametroRepository);
    }

    @Test
    void consultarInfo_DevuelveAppYUrl() {
        configurarUrl("https://sigapp-api-dev.saas.arqbs.com");

        ObjectResponse response = servidorInfoService.consultarInfo();

        assertEquals(0, response.getCode());
        ServidorInfoResponse info = (ServidorInfoResponse) response.getObject();
        assertEquals("SIGAPP", info.getApp());
        assertEquals("https://sigapp-api-dev.saas.arqbs.com", info.getUrlPublica());
    }

    /**
     * La app le concatena sus rutas a este valor: una barra final o mayusculas
     * en un UPDATE manual no deben llegarle.
     */
    @ParameterizedTest
    @ValueSource(strings = {
            "https://sigapp-api-dev.saas.arqbs.com/",
            "  https://sigapp-api-dev.saas.arqbs.com  ",
            "HTTPS://SIGAPP-API-DEV.SAAS.ARQBS.COM"
    })
    void consultarInfo_NormalizaLaUrl(String valor) {
        configurarUrl(valor);

        ObjectResponse response = servidorInfoService.consultarInfo();

        assertEquals(0, response.getCode());
        assertEquals("https://sigapp-api-dev.saas.arqbs.com",
                ((ServidorInfoResponse) response.getObject()).getUrlPublica());
    }

    @Test
    void consultarInfo_ConservaElPuerto() {
        configurarUrl("https://sigapp-api-dev.saas.arqbs.com:8443");

        ObjectResponse response = servidorInfoService.consultarInfo();

        assertEquals("https://sigapp-api-dev.saas.arqbs.com:8443",
                ((ServidorInfoResponse) response.getObject()).getUrlPublica());
    }

    @Test
    void consultarInfo_SinParametro_RespondeError() {
        when(parametroRepository.buscarPorCodigo(PARAMETRO_URL)).thenReturn(Optional.empty());

        ObjectResponse response = servidorInfoService.consultarInfo();

        assertEquals(-1, response.getCode());
        assertNull(response.getObject());
    }

    /**
     * Un valor que no sea un origen https no se reparte a los dispositivos:
     * incluye el marcador que deja el script si se aplica sin editar.
     */
    @ParameterizedTest
    @ValueSource(strings = {
            ">>> DEFINIR <<<",
            "   ",
            "http://sigapp-api-dev.saas.arqbs.com",
            "sigapp-api-dev.saas.arqbs.com",
            "https://sigapp-api-dev.saas.arqbs.com/api/v1",
            "https://sigapp-api-dev.saas.arqbs.com?x=1",
            "https://usuario@sigapp-api-dev.saas.arqbs.com"
    })
    void consultarInfo_UrlInvalida_RespondeError(String valor) {
        configurarUrl(valor);

        ObjectResponse response = servidorInfoService.consultarInfo();

        assertEquals(-1, response.getCode());
        assertNull(response.getObject());
    }

    @Test
    void consultarInfo_ValorNulo_RespondeError() {
        configurarUrl(null);

        ObjectResponse response = servidorInfoService.consultarInfo();

        assertEquals(-1, response.getCode());
    }

    private void configurarUrl(String valor) {
        when(parametroRepository.buscarPorCodigo(PARAMETRO_URL))
                .thenReturn(Optional.of(new ParametroModel(PARAMETRO_URL, valor)));
    }
}
