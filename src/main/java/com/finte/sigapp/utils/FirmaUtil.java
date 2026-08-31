package com.finte.sigapp.utils;

import com.finte.sigapp.exception.BussinessException;
import com.finte.sigapp.exception.catalog.ErrorCode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;

import java.util.Base64;
import java.util.regex.Pattern;

/**
 * Validacion y normalizacion de las firmas de un traspaso.
 *
 * El front envia la firma como imagen en base64, tal cual la produce un canvas
 * ({@code toDataURL()}), por lo que puede venir con el prefijo
 * {@code data:image/png;base64,}. En FI_MOVITRAS se guardan los BYTES (BLOB),
 * asi que aqui se limpia, se decodifica y se comprueba que lo recibido sea de
 * verdad una imagen antes de mandarla al paquete PL/SQL.
 *
 * Los rechazos son de negocio, no tecnicos: se lanzan como
 * {@link BussinessException} para que el service los traduzca a un
 * {@code code -1} en vez de a un 500.
 */
@Slf4j
@Component
public class FirmaUtil {

    /**
     * Prefijo de un data URL. Se acepta pero no se guarda: lo que interesa son
     * los bytes, y el tipo real se deduce despues de la cabecera de la imagen.
     */
    private static final Pattern PREFIJO_DATA_URL =
            Pattern.compile("^data:image/(png|jpeg|jpg);base64,", Pattern.CASE_INSENSITIVE);


    private final int maxBytes;

    public FirmaUtil(@Value("${app.traspasos.firma.max-bytes:512000}") int maxBytes) {
        this.maxBytes = maxBytes;
    }

    /**
     * Convierte el base64 recibido del front en los bytes que se guardan en el
     * BLOB.
     *
     * @param firmaBase64 imagen en base64, con o sin prefijo data URL
     * @return bytes de la imagen
     * @throws BussinessException si no es base64 valido, si excede el tope
     *                            configurado o si no es un PNG/JPEG
     */
    public byte[] decodificar(String firmaBase64) {

        if (firmaBase64 == null || firmaBase64.isBlank()) {
            throw new BussinessException(ErrorCode.SIGAPP_400, "La firma es obligatoria");
        }

        String limpia = PREFIJO_DATA_URL.matcher(firmaBase64.trim()).replaceFirst("");
        limpia = sinEspacios(limpia);

        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(limpia);
        } catch (IllegalArgumentException e) {
            log.warn("Firma rechazada: no es base64 valido - {}", e.getMessage());
            throw new BussinessException(ErrorCode.SIGAPP_400, "La firma no es base64 valido");
        }

        if (bytes.length == 0) {
            throw new BussinessException(ErrorCode.SIGAPP_400, "La firma llego vacia");
        }

        if (bytes.length > maxBytes) {
            log.warn("Firma rechazada: {} bytes supera el maximo de {}", bytes.length, maxBytes);
            throw new BussinessException(ErrorCode.SIGAPP_400,
                    "La firma supera el tamano maximo permitido (" + maxBytes + " bytes)");
        }

        if (!esPng(bytes) && !esJpeg(bytes)) {
            log.warn("Firma rechazada: los bytes no corresponden a un PNG ni a un JPEG");
            throw new BussinessException(ErrorCode.SIGAPP_400, "La firma no es una imagen PNG o JPEG");
        }

        return bytes;
    }

    /**
     * El base64 MIME viene partido en lineas, y el decoder estricto no las admite.
     * Se recorre en vez de usar un regex para no depender de escapes en el fuente.
     */
    private static String sinEspacios(String texto) {
        StringBuilder limpio = new StringBuilder(texto.length());
        for (int i = 0; i < texto.length(); i++) {
            char caracter = texto.charAt(i);
            if (!Character.isWhitespace(caracter)) {
                limpio.append(caracter);
            }
        }
        return limpio.toString();
    }

    /**
     * Tipo de contenido con el que se sirve la firma. Se deduce de la cabecera de
     * la imagen, no de lo que haya declarado el cliente en el data URL.
     */
    public static MediaType mime(byte[] bytes) {
        return esPng(bytes) ? MediaType.IMAGE_PNG : MediaType.IMAGE_JPEG;
    }

    /** Cabecera PNG: los cuatro primeros bytes son 89 50 4E 47. */
    private static boolean esPng(byte[] b) {
        return b != null
                && b.length > 8
                && (b[0] & 0xFF) == 0x89
                && b[1] == 'P'
                && b[2] == 'N'
                && b[3] == 'G';
    }

    /** Cabecera JPEG: FF D8 FF. */
    private static boolean esJpeg(byte[] b) {
        return b != null
                && b.length > 3
                && (b[0] & 0xFF) == 0xFF
                && (b[1] & 0xFF) == 0xD8
                && (b[2] & 0xFF) == 0xFF;
    }
}
