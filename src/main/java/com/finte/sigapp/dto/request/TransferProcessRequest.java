package com.finte.sigapp.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class TransferProcessRequest {

    @NotBlank(message = "El estado es obligatorio")
    @Pattern(regexp = "^(ap|na)$", message = "El estado solo puede ser 'ap' (aprobado) o 'na' (rechazado)")
    private String estado;

    /**
     * Obligatoria al rechazar (se valida en el service). Al aprobar es opcional.
     * El limite acompana a MOTRMORE / MOTROBSE, ambas VARCHAR2(500).
     */
    @Size(max = 500, message = "La observación no puede exceder los 500 caracteres")
    private String observacion;
}
