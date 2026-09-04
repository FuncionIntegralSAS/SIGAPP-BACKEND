package com.finte.sigapp.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
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
@Entity
@Table(name = "FI_ACFIGEOL")
public class AcfigeolEntity {

    @Id
    @Column(name = "AFGEIDRE", nullable = false)
    private Long afgeIdre;

    @Column(name = "AFGELATI", nullable = false, precision = 9, scale = 6)
    private BigDecimal afgeLati;

    @Column(name = "AFGELONG", nullable = false, precision = 9, scale = 6)
    private BigDecimal afgeLong;

    @Column(name = "AFGEFCRE", nullable = false, insertable = false, updatable = false)
    private LocalDateTime afgeFcre;

    @Column(name = "AFGEUCRE", nullable = false, length = 30, insertable = false, updatable = false)
    private String afgeUcre;

    @Column(name = "AFGEFEDI", insertable = false, updatable = false)
    private LocalDateTime afgeFedi;

    @Column(name = "AFGEUEDI", length = 30, insertable = false, updatable = false)
    private String afgeUedi;
}
