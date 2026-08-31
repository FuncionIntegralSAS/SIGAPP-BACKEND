package com.finte.sigapp.utils;

import com.finte.sigapp.exception.BussinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Base64;

import static org.junit.jupiter.api.Assertions.*;

class FirmaUtilTest {

    private static final int MAX_BYTES = 1_024;

    private final FirmaUtil firmaUtil = new FirmaUtil(MAX_BYTES);

    /** Cabecera PNG. Es lo unico que mira la validacion. */
    private static byte[] png() {
        return new byte[] { (byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A, 0x00, 0x00, 0x00, 0x0D };
    }

    /** Cabecera JPEG (SOI + marcador). */
    private static byte[] jpeg() {
        return new byte[] { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10 };
    }

    private static String base64(byte[] bytes) {
        return Base64.getEncoder().encodeToString(bytes);
    }

    @Test
    @DisplayName("decodifica un PNG en base64 plano")
    void decodifica_PngPlano() {
        assertArrayEquals(png(), firmaUtil.decodificar(base64(png())));
    }

    @Test
    @DisplayName("decodifica un JPEG en base64 plano")
    void decodifica_JpegPlano() {
        assertArrayEquals(jpeg(), firmaUtil.decodificar(base64(jpeg())));
    }

    @Test
    @DisplayName("quita el prefijo data URL, sin importar mayusculas ni el subtipo")
    void decodifica_QuitaPrefijoDataUrl() {
        assertArrayEquals(png(), firmaUtil.decodificar("data:image/png;base64," + base64(png())));
        assertArrayEquals(jpeg(), firmaUtil.decodificar("DATA:IMAGE/JPEG;BASE64," + base64(jpeg())));
        assertArrayEquals(jpeg(), firmaUtil.decodificar("data:image/jpg;base64," + base64(jpeg())));
    }

    @Test
    @DisplayName("tolera espacios y saltos de linea del base64 MIME")
    void decodifica_ToleraSaltosDeLinea() {
        String conSaltos = "  " + base64(png()).replaceAll("(.{4})", "$1\n") + "\n";
        assertArrayEquals(png(), firmaUtil.decodificar(conSaltos));
    }

    @Test
    @DisplayName("rechaza null y cadena en blanco")
    void rechaza_Vacia() {
        assertThrows(BussinessException.class, () -> firmaUtil.decodificar(null));
        assertThrows(BussinessException.class, () -> firmaUtil.decodificar("   "));
    }

    @Test
    @DisplayName("rechaza lo que no es base64")
    void rechaza_Base64Invalido() {
        BussinessException e = assertThrows(BussinessException.class,
                () -> firmaUtil.decodificar("no-es-base64-***"));
        assertTrue(e.getMessage().contains("base64"));
    }

    @Test
    @DisplayName("rechaza base64 valido cuyo contenido no es PNG ni JPEG")
    void rechaza_ContenidoQueNoEsImagen() {
        BussinessException e = assertThrows(BussinessException.class,
                () -> firmaUtil.decodificar(base64("GIF89a-y-algo-mas".getBytes())));
        assertTrue(e.getMessage().contains("PNG"));
    }

    @Test
    @DisplayName("el tope aplica sobre los bytes decodificados, no sobre el base64")
    void rechaza_PorTamano() {
        // El base64 pesa ~33% mas: una imagen justo bajo el tope debe pasar aunque su
        // representacion en texto lo supere.
        byte[] justoBajoElTope = new byte[MAX_BYTES];
        System.arraycopy(png(), 0, justoBajoElTope, 0, png().length);
        assertTrue(base64(justoBajoElTope).length() > MAX_BYTES);
        assertDoesNotThrow(() -> firmaUtil.decodificar(base64(justoBajoElTope)));

        byte[] excedido = new byte[MAX_BYTES + 1];
        System.arraycopy(png(), 0, excedido, 0, png().length);
        BussinessException e = assertThrows(BussinessException.class,
                () -> firmaUtil.decodificar(base64(excedido)));
        assertTrue(e.getMessage().contains("tamano maximo"));
    }

    @Test
    @DisplayName("el content type sale de la cabecera de la imagen")
    void mime_SaleDeLaCabecera() {
        assertEquals("image/png", FirmaUtil.mime(png()).toString());
        assertEquals("image/jpeg", FirmaUtil.mime(jpeg()).toString());
    }
}
