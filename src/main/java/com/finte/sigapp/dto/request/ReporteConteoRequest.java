package com.finte.sigapp.dto.request;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ReporteConteoRequest {

    @NotBlank(message = "Bodega es obligatoria")
    private String bodega;

    @NotNull(message = "Numero de conteo es obligatorio")
    @Min(value = 1, message = "Numero de conteo debe ser mayor o igual a 1")
    @Max(value = 3, message = "Numero de conteo debe ser menor o igual a 3")
    private Integer numeroConteo;

    @Valid
    @NotNull(message = "Lista de articulos es obligatoria")
    private List<ArticuloConteo> articulos;

}
