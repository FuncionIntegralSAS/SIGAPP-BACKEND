package com.finte.sigapp.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "CONTARBO")
@IdClass(ContarboPK.class)
public class ContarboEntity {
    @Id
    @Column(name = "COABEMPR", length = 6)
    private String coabEmpr;

    @Id
    @Column(name = "COABBODE", length = 4)
    private String coabBode;

    @Id
    @Column(name = "COABBOLO", length = 4)
    private String coabBolo;

    @Id
    @Column(name = "COABFECH")
    private LocalDateTime coabFech;

    @Id
    @Column(name = "COABARTI", length = 50)
    private Long coabArti;

    @Id
    @Column(name = "COABPLAC", length = 50)
    private String coabPlac;

    @Column(name = "COABCAC1", precision = 12, scale = 4)
    private BigDecimal coabCac1;

    @Column(name = "COABCAC2", precision = 12, scale = 4)
    private BigDecimal coabCac2;

    @Column(name = "COABCAC3", precision = 12, scale = 4)
    private BigDecimal coabCac3;

    @Column(name = "COABCABO", precision = 12, scale = 4)
    private BigDecimal coabCabo;

    @Column(name = "COABDIEM")
    private String coabDiem;

    @Column(name = "COABDITD")
    private String coabDitd;

    @Column(name = "COABDIND", precision = 9, scale = 0)
    private BigDecimal coabDind;

    @Column(name = "COABFESI")
    private LocalDateTime coabFesi;

    @Column(name = "COABDIGI")
    private String coabDigi;

    @Column(name = "COABTERM")
    private String coabTerm;

    @Column(name = "COABESTA")
    private String coabEsta;

    @Column(name = "COABVAUN", precision = 12, scale = 0)
    private BigDecimal coabVaun;

    @Column(name = "COABFECI")
    private LocalDateTime coabFeci;

}
