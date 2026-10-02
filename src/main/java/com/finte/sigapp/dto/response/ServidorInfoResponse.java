package com.finte.sigapp.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Identidad del backend, para que la app movil valide la URL que acaba de leer
 * de un QR antes de guardarla.
 *
 * {@code app} es una constante: le permite a la app distinguir un SIGAPP de
 * cualquier otro servidor que conteste 200 en esa ruta.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ServidorInfoResponse {

    private String app;
    private String urlPublica;
}
