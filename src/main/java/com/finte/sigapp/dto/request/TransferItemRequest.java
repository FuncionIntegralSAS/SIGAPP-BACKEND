package com.finte.sigapp.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Un articulo dentro de la lista de {@link TransferRequest}.
 *
 * Solo lleva lo que es propio de la linea. La empresa, las personas, la
 * observacion y el tipo de movimiento van en la raiz del request porque son del
 * documento: dentro del array abririan la puerta a que dos articulos declararan
 * personas distintas.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class TransferItemRequest {

    @NotBlank(message = "El articulo es obligatorio")
    @Size(max = 20, message = "El articulo no puede exceder 20 caracteres")
    private String articulo;

    /**
     * Opcional: hay elementos sin placa. Se normaliza a "." antes de guardar,
     * igual que ACTIFIJO, porque un null no deduplica en el indice unico de la
     * linea y el mismo activo podria cargarse dos veces.
     */
    @Size(max = 20, message = "La placa no puede exceder 20 caracteres")
    private String placa;

    /** Identidad del activo dentro del tramite, ya normalizada. */
    public String clave() {
        return articulo + "|" + (placa == null || placa.isBlank() ? "." : placa.trim());
    }
}
