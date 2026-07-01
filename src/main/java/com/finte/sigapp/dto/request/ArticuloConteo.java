package com.finte.sigapp.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ArticuloConteo {
    @NotBlank(message = "Id articulo es obligatorio")
    private Long idArticulo;

    @NotNull(message = "Cantidad contada es obligatoria")
    private Long cantidad;
}
