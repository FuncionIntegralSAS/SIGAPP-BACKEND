package com.finte.sigapp.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BodegaDtoResponse {
    private String bodeCodi;
    private String bodeDesc;
    private String bodeEsta;
}
