package com.finte.sigapp.model;

import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;
import lombok.Builder;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BodegaModel {
    private String bodeEmpr; // Empresa
    private String bodeCodi; // PK: Código único
    private String bodeDesc; // Descripción
    private String bodeTibo; // Tipo (FI=Fisica, PE=Personal)
    private String bodeClbo; // Clase
    private String bodeEsta; // Estado
    private String bodeResp; // Responsable (FK a Personal?)
    private String bodeDire; // Dirección
    private String bodeTel1; // Teléfono
    private LocalDateTime bodeFeuc; // Fecha última modificación
    private String bodeDivi;
}