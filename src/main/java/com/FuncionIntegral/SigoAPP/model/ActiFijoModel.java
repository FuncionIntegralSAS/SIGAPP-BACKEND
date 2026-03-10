package com.FuncionIntegral.SigoAPP.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ActiFijoModel {
    private String acfiArti; // Código Artículo
    private String acfiPlac; // Placa
    private String acfiBode; // Bodega
    private String acfiObse; // Descripción/Observación
    private String acfiNuse; // Número de serie
    private BigDecimal acfiCoac; // Costo Actual
    private BigDecimal acfiCohi; // Costo Histórico
    private String acfiEsta; // Estado del registro (ac = activo)
    private String acfiEsac; // Estado físico del activo (Operativo, Dañado, etc.)
    private Long acfiPers; // Código del responsable
}