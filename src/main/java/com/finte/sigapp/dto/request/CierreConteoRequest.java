package com.finte.sigapp.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class CierreConteoRequest {
    @NotBlank(message = "La bodega es obligatoria")
    private String bodega;

    @NotBlank(message = "La empresa es obligatoria")
    private String empresa;
}
