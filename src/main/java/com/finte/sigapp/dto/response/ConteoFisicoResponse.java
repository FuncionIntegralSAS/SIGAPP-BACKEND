package com.finte.sigapp.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class ConteoFisicoResponse {
    private boolean success;
    private String message;
}
