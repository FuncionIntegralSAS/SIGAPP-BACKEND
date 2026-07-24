package com.finte.sigapp.model;

import lombok.Data;
import java.time.LocalDate;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Entity
@Table(name = "USUABADA")
@Data
public class UsabadaModel {
    @Id
    private String usbdcodi; // PK: Username
    private String usbddesc; // Nombre descriptivo
    private String usbdcont; // Password (HASH)
    private LocalDate usbdexco; // Expiración contraseña
    private Long usbdinfa; // Intentos fallidos
    private String usbdesta; // Estado
}