package com.finte.sigapp.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

/**
 * Registro de la firma de aceptacion de un traspaso.
 *
 * La firma viaja como imagen PNG en base64 y se guarda en el CLOB MOTRFIFU o
 * MOTRFIDE segun el tipo. No se limita el tamano por anotacion: es un CLOB y el
 * tope real lo impone el tamano maximo de request configurado en el servidor.
 */
@Data
public class TransferSignRequest {

    @NotBlank(message = "El tipo de firma es obligatorio")
    @Pattern(regexp = "^(FU|DE)$", message = "El tipo de firma solo puede ser 'FU' (fuente) o 'DE' (destino)")
    private String tipoFirma;

    @NotBlank(message = "La firma es obligatoria")
    private String firma;
}
