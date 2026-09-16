package com.finte.sigapp.dto.request;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AcfigeolRequest {
    private Long afgeIdre;
    private BigDecimal afgeLati;
    private BigDecimal afgeLong;
}
