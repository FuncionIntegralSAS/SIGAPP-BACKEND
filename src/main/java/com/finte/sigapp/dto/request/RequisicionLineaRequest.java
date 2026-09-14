package com.finte.sigapp.dto.request;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.util.List;

/**
 * Una linea de la requisicion sobre la que se actua, con su cantidad.
 *
 * Las tres columnas de identificacion van juntas porque dentro de una
 * requisicion la linea es el trio (articulo, bodega, secuencia): el mismo
 * articulo puede aparecer dos veces en distinta bodega o secuencia, asi que
 * ninguna sobra.
 *
 * Que significa {@code cantidad} depende de la operacion: aprobada al aprobar,
 * entregada al entregar y recibida al registrar. Nunca puede ser cero.
 *
 * {@code placas} solo aplica a articulos devolutivos, que el ERP exige que
 * lleven tantas placas como unidades se mueven.
 *
 * AL ENTREGAR ES OPCIONAL. Si no viene, el paquete las elige por orden de
 * entrada del activo -lo primero que entro es lo primero que sale- y quien
 * despacha recibe la lista ya resuelta. Enviarlas es el caso de excepcion: la
 * placa mas antigua esta danada o no aparece en el estante.
 *
 * AL REGISTRAR son las que efectivamente se reciben, y ahi si hay que enviarlas
 * cuando se recibe menos de lo entregado.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RequisicionLineaRequest {

    @NotBlank
    @Size(max = 50)
    private String articulo;

    @NotBlank
    @Size(max = 4)
    private String bodega;

    @NotNull
    private BigDecimal secuencia;

    /**
     * Las unidades del ERP admiten decimales, asi que no se valida como entero.
     * Para los devolutivos es el propio ERP quien exige que sea entero.
     */
    @NotNull
    @DecimalMin(value = "0.000001", message = "la cantidad debe ser mayor que cero")
    private BigDecimal cantidad;

    private List<String> placas;
}
