package com.finte.sigapp.dto.response;

import lombok.Builder;
import lombok.Data;

@Data
@Builder
public class AcfigeolActionResponse {
    private boolean success;
    private String message;
}
