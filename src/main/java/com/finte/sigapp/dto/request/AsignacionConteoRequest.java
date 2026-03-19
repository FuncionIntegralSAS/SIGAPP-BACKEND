package com.finte.sigapp.dto.request;

import com.finte.sigapp.dto.UsuarioConteoDTO;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.List;

@Data
public class AsignacionConteoRequest {
    private String empresa;
    private String bodega;
    private LocalDateTime fechaConteo;
    private List<UsuarioConteoDTO> usuarios;
}
