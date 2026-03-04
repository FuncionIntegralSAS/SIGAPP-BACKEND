package com.FuncionIntegral.SigoAPP.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class TransferProcessRequest {

    @NotBlank(message = "El estado es obligatorio")
    @Pattern(regexp = "^(ap|na)$", message = "El estado solo puede ser 'ap' (aprobado) o 'na' (rechazado)")
    private String estado;

    @Size(max = 4000, message = "La observación no puede exceder los 4000 caracteres")
    private String observacion; // Opcional
}