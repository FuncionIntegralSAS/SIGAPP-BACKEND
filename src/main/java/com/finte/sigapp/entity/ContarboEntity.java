package com.finte.sigapp.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.math.BigDecimal;
import java.math.BigInteger;
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
    @Column(name = "COABBOLO",length = 4)
    private String coabBolo;

    @Id
    @Column(name = "COABFECH")
    private LocalDateTime coabFech;

    @Id
    @Column(name = "COABARTI", length = 50)
    private String coabArti;

    @Id
    @Column(name = "COABPLAC",length = 50)
    private String coabPlac;

    @Column(name = "COABCAC1")
    private BigDecimal coabCac1;

    @Column(name = "COABCAC2")
    private BigDecimal coabCac2;

    @Column(name = "COABCAC3")
    private BigDecimal coabCac3;

    @Column(name = "COABCABO")
    private BigDecimal coabCabo;

    @Column(name = "COABDIEM")
    private String coabDiem;

    @Column(name = "COABDITD")
    private String coabDitd;

    @Column(name = "COABDIND")
    private BigInteger coabDind;

    @Column(name = "COABFESI")
    private LocalDateTime coabFesi;

    @Column(name = "COABDIGI")
    private String coabDigi;

    @Column(name = "COABTERM")
    private String coabTerm;

    @Column(name = "COABESTA")
    private String coabEsta;

    @Column(name = "COABVAUN")
    private BigInteger coabVaun;

    @Column(name = "COABFECI")
    private LocalDateTime coabFeci;

}
