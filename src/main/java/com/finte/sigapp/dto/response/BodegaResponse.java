package com.finte.sigapp.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class BodegaResponse {
    private String id;
    private String name;
    private String divisionId;
}