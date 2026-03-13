package com.finte.sigapp.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class EmpresaDtoResponse {
    private String codigo;
    private String descripcion;
    private String nit;
    private String estado;
}
