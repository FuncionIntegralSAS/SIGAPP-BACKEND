package com.finte.sigapp.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Cuerpo del registro de una firma.
 *
 * No hay orden entre las dos: cualquiera puede llegar primero y ninguna mueve
 * el estado de la requisicion, que se queda en entregado hasta que se registra
 * la salida.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RequisicionFirmaRequest {

    /** SA para la firma de salida (quien entrega), RE para la de recibe. */
    @NotBlank
    @Pattern(regexp = "^(SA|RE)$", message = "el tipo de firma debe ser SA (salida) o RE (recibe)")
    private String tipo;

    /** Cedula de quien firma. Queda guardada junto al instante de la firma. */
    @NotBlank
    @Size(max = 20)
    private String persona;

    /**
     * Imagen PNG o JPEG en base64, con o sin el prefijo {@code data:image/...}.
     *
     * El base64 es formato de transporte: se decodifica antes de guardar y lo
     * que persiste son los bytes. Sin {@code @Size} porque el limite real lo
     * aplica FirmaUtil sobre los bytes ya decodificados, que es lo que ocupa.
     */
    @NotBlank
    private String firma;
}
