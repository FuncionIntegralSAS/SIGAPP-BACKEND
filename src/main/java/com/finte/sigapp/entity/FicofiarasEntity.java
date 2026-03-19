package com.finte.sigapp.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "FI_COFIARAS")
public class FicofiarasEntity {
    @Id
    @Column(name = "ARASIDAS")
    private Long ARASIDAS;

    @Column(name = "ARASIDCO")
    private Long ARASIDCO;

    @Column(name = "ARASIDBO")
    private Long ARASIDBO;

    @Column(name = "ARASIDAR")
    private Long ARASIDAR;

    @Column(name = "ARASIDUS")
    private Long ARASIDUS;

    @Column(name = "ARASNUCO")
    private Integer ARASNUCO;

    @Column(name = "ARASCOQR")
    private String ARASCOQR;

    @Column(name = "ARASCANT")
    private Long ARASCANT;

    @Column(name = "ARASFECO")
    private LocalDateTime ARASFECO;

    @Column(name = "ARASEMPR")
    private String ARASEMPR;

    @Column(name = "ARASPLAC")
    private String ARASPLAC;

    @Column(name = "ARASESTA")
    private String ARASESTA;

    @Column(name = "ARASSINC")
    private String ARASSINC;
}
