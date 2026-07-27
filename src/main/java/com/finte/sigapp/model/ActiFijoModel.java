package com.finte.sigapp.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "ACTIFIJO")
public class ActiFijoModel {
    @Id
    @Column(name = "ACFIARTI")
    private String acfiarti; // Código Artículo
    // @Column(name = "ACFIPLAC")
    // private String acfiPlac; // Placa
    // @Column(name = "ACFIBODE")
    // private String acfiBode; // Bodega
    @Column(name = "ACFIOBSE")
    private String acfiobse; // Descripción/Observación
    // @Column(name = "ACFINUSE")
    // private String acfiNuse; // Número de serie
    // @Column(name = "ACFICOAC")
    // private BigDecimal acfiCoac; // Costo Actual
    // @Column(name = "ACFICOHI")
    // private BigDecimal acfiCohi; // Costo Histórico
    // @Column(name = "ACFIESTA")
    // private String acfiEsta; // Estado del registro (ac = activo)
    // @Column(name = "ACFIESAC")
    // private String acfiEsac; // Estado físico del activo (Operativo, Dañado,
    // etc.)
    // @Column(name = "ACFIPERS")
    // private String acfiPers; // Código del responsable
}