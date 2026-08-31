package com.finte.sigapp.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Alta de un traspaso.
 *
 * No incluye la llave del documento: la requisicion REQUSUMI la crea el paquete
 * PL/SQL como parte de la misma transaccion, y el tipo y numero se devuelven en
 * la respuesta.
 *
 * Los tamanos maximos replican los de la tabla para que una entrada invalida se
 * rechace con 400 antes de llegar a Oracle, en vez de reventar con ORA-12899.
 */
@Data
public class TransferRequest {

    @NotBlank(message = "La empresa es obligatoria")
    @Size(max = 6, message = "La empresa no puede exceder 6 caracteres")
    private String empresa;

    @NotBlank(message = "La persona fuente es obligatoria")
    @Size(max = 20, message = "La persona fuente no puede exceder 20 caracteres")
    private String personaFuente;

    @NotBlank(message = "La persona destino es obligatoria")
    @Size(max = 20, message = "La persona destino no puede exceder 20 caracteres")
    private String personaDestino;

    @NotBlank(message = "El elemento es obligatorio")
    @Size(max = 20, message = "El elemento no puede exceder 20 caracteres")
    private String elemento;

    @Size(max = 20, message = "La placa no puede exceder 20 caracteres")
    private String placa;

    @NotBlank(message = "El motivo es obligatorio")
    @Size(max = 500, message = "La observación no puede exceder 500 caracteres")
    private String observacion;

    /**
     * Tipo de documento con el que se crea la requisicion. Opcional: si no se
     * envia, el paquete lo resuelve con FunBuscPara
     */
    @Size(max = 4, message = "El tipo de movimiento no puede exceder 4 caracteres")
    private String tipoMovimiento;
}
