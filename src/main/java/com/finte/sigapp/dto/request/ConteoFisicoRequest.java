package com.finte.sigapp.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Date;

@Data
public class ConteoFisicoRequest {

    @NotBlank(message = "La empresa es obligatoria")
    private String empresa;

    @NotBlank(message = "La bodega es obligatoria")
    private String bodega;

    private String bodegaLogica;

    @NotBlank(message = "La bodega es obligatoria")
    private String articulo;

    @NotNull(message = "La fecha es obligatoria")
    private LocalDateTime fecha;

    @NotNull(message = "El verificarExistencia es obligatorio")
    private String verificarExistencia;
}
