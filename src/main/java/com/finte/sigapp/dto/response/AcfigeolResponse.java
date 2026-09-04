package com.finte.sigapp.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AcfigeolResponse {
    private Long afgeIdre;
    private BigDecimal afgeLati;
    private BigDecimal afgeLong;
    private LocalDateTime afgeFcre;
    private String afgeUcre;
    private LocalDateTime afgeFedi;
    private String afgeUedi;
}
