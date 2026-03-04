package com.FuncionIntegral.SigoAPP.dto.response;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
public class PersonResponse {
    private String id;
    private String nationalId; // Mapeado de PERSDOID
    private String fullName;    // Concatenación de PERSNOMB y PERSAPEL
    private Boolean accountExists;
    private Boolean isActive;
    private LocalDateTime creationDate;
    private String createdByUserId;
    private String divisionId;
}