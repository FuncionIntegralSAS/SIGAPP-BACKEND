package com.finte.sigapp.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class LoginContadorRequest {

    @NotBlank(message = "el documento es obligatorio")
    private String documento;

    @NotBlank(message = "la codigo temporal es obligatorio")
    private String codigoTemporal;

}
