package com.finte.sigapp.service.impl;

import com.finte.sigapp.dto.response.ObjectResponse;
import com.finte.sigapp.dto.response.ServidorInfoResponse;
import com.finte.sigapp.model.ParametroModel;
import com.finte.sigapp.repository.ParametroRepository;
import com.finte.sigapp.service.ServidorInfoService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.util.Optional;

/**
 * Identidad del backend para la configuracion dinamica de la app movil.
 *
 * La app es un unico APK para todos los ambientes: lee de un QR la URL del
 * backend y la valida contra este servicio antes de guardarla.
 *
 * Toda la configuracion vive en PARAOPER y nada en el despliegue, a proposito:
 * un cambio de URL se resuelve con un UPDATE, sin redesplegar, sin reiniciar el
 * pod y sin depender de infraestructura. Por lo mismo no se cachea: el parametro
 * se lee en cada consulta y el UPDATE tiene efecto inmediato.
 *
 * La contrapartida es que una base clonada de otro ambiente trae la URL de ese
 * ambiente. No hay nada aqui que lo detecte: corregir URLSIGAPP es un paso
 * obligatorio despues de clonar o refrescar una base.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class ServidorInfoServiceImpl implements ServidorInfoService {

    private static final int CODE_OK = 0;
    private static final int CODE_ERROR = -1;

    private static final String APP = "SIGAPP";
    private static final String PARAMETRO_URL = "URLSIGAPP";

    private final ParametroRepository parametroRepository;

    @Override
    public ObjectResponse consultarInfo() {
        Optional<ParametroModel> parametro = parametroRepository.buscarPorCodigo(PARAMETRO_URL);

        if (parametro.isEmpty()) {
            log.error("Falta el parametro {} en PARAOPER: aplicar db/configuracion/01_PARAOPER_URLSIGAPP.sql",
                    PARAMETRO_URL);
            return new ObjectResponse(CODE_ERROR,
                    "La URL publica no esta configurada: falta el parametro " + PARAMETRO_URL);
        }

        String urlPublica = normalizar(parametro.get().getValor());
        if (urlPublica == null) {
            log.error("El parametro {} tiene un valor invalido: [{}]", PARAMETRO_URL, parametro.get().getValor());
            return new ObjectResponse(CODE_ERROR,
                    "La URL publica configurada en " + PARAMETRO_URL + " no es valida: debe tener la forma https://host");
        }

        ServidorInfoResponse info = ServidorInfoResponse.builder()
                .app(APP)
                .urlPublica(urlPublica)
                .build();

        return new ObjectResponse(CODE_OK, "Consulta exitosa", info);
    }

    /**
     * Devuelve la URL sin barra final, o nulo si no sirve como URL base.
     *
     * Se exige https porque Android bloquea el trafico en claro, y se rechaza
     * cualquier ruta, consulta o usuario embebido: la app le concatena sus
     * propias rutas, asi que aqui solo puede venir el origen. Un valor mal
     * escrito en un UPDATE manual se detiene aqui en vez de repartirse a todos
     * los dispositivos.
     */
    private String normalizar(String valor) {
        if (valor == null || valor.isBlank()) {
            return null;
        }

        URI uri;
        try {
            uri = URI.create(valor.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }

        boolean esHttps = "https".equalsIgnoreCase(uri.getScheme());
        boolean sinRuta = uri.getRawPath() == null || uri.getRawPath().isEmpty() || uri.getRawPath().equals("/");
        boolean soloOrigen = sinRuta && uri.getRawQuery() == null && uri.getRawFragment() == null
                && uri.getRawUserInfo() == null;

        if (!esHttps || uri.getHost() == null || !soloOrigen) {
            return null;
        }

        String puerto = uri.getPort() == -1 ? "" : ":" + uri.getPort();
        return "https://" + uri.getHost().toLowerCase() + puerto;
    }
}
