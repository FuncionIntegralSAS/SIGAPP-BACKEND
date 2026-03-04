package com.FuncionIntegral.SigoAPP.dto.request;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

@Data
public class TransferRequest {

    @NotBlank(message = "El ID del artículo es obligatorio")
    private String artiId; // El código del artículo

    @NotBlank(message = "La placa es obligatoria")
    private String placa;

    @NotBlank(message = "El responsable destino es obligatorio")
    private String personaDestino;

    @NotBlank(message = "El motivo es obligatorio")
    private String observacion;

    @NotBlank(message = "La empresa es obligatoria")
    private String empresa;

    @NotBlank(message = "El Tipo Movimiento es obligatorio")
    private String tipoMov;
}