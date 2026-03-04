package com.FuncionIntegral.SigoAPP.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import java.time.LocalDateTime;
import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UsabadaModel {
    private String usbdCodi; // PK: Username
    private String usbdDesc; // Nombre descriptivo
    private String usbdCont; // Password (HASH)
    private String usbdEsta; // Estado
    private BigDecimal usbdInfa; // Intentos fallidos
    private LocalDateTime usbdExco; // Expiración contraseña
    private LocalDateTime usbdCrea; // Fecha creación
    private String usbdHash; // Token/Hash temporal
}