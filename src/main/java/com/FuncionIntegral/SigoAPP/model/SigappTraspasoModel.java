package com.FuncionIntegral.SigoAPP.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SigappTraspasoModel {
    private Long trasId;
    private String trasEmpresa;
    private String trasTipoMovi;
    private String trasArti; // FK a ActiFijo
    private String trasPlaca;  // Placa Articulo
    private String trasPersDest; // FK a Personal (Destino)
    private String trasObserv;
    private String trasEstado;   // Estado del trámite
    private String trasRech;

    // Auditoría
    private LocalDateTime trasFechCrea;
    private LocalDateTime trasFechSis; // Fecha sistema/actualización
    private String trasDigitador; // Usuario digitador

    private String personaFuenteDesc;   // PERSCODI - PERSNOMB PERSAPEL
    private String bodegaFuenteDesc;    // BODECODI - BODEDESC
    private String bodegaDestinoDesc;   // BODECODI - BODEDESC
    private String personaDestinoDesc;
}