package com.finte.sigapp.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

/**
 * Identificacion de una linea, sin cantidad. La usa la anulacion, que es total
 * sobre el saldo aprobado y por tanto no recibe cuanto.
 *
 * Repite los tres campos de {@link RequisicionLineaRequest} en vez de heredar
 * de una base comun: con Lombok, {@code @Data} sobre una jerarquia genera un
 * {@code equals} que no considera los campos del padre y da problemas
 * silenciosos. Tres campos duplicados salen mas baratos que eso.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RequisicionLineaClaveRequest {

    @NotBlank
    @Size(max = 50)
    private String articulo;

    @NotBlank
    @Size(max = 4)
    private String bodega;

    @NotNull
    private BigDecimal secuencia;
}
