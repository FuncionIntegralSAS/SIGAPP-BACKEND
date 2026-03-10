package com.FuncionIntegral.SigoAPP.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.Date;

@Data
public class ConteoFisicoRequest {

    @NotBlank(message = "La empresa es obligatoria")
    private String empresa;

    @NotBlank(message = "La bodega es obligatoria")
    private String bodega;

    @NotBlank(message = "La bodega es obligatoria")
    private String bodegaLogica;

    @NotBlank(message = "La bodega es obligatoria")
    private String articulo;

    @NotNull(message = "La fecha es obligatoria")
    private Date fecha;

    @NotNull(message = "El verificarExistencia es obligatorio")
    private String verificarExistencia;
}
