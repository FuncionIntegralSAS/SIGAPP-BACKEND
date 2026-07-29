package com.finte.sigapp.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class RoleformResponse {
    private String usuario;
    private String forma;
    private String tipoRol;
    private String tipoForma;
    private String producto;
}
