package com.finte.sigapp.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class PersonResponse {
    private String cedula;
    private String nombre;
    private String apellido;
    private String correo;
    private String division;
    private String estado;
}