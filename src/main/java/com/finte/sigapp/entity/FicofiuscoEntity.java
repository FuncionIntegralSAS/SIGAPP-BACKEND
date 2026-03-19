package com.finte.sigapp.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "FI_COFIUSCO")
public class FicofiuscoEntity {

    @Id
    @Column(name = "USCOIDUS")
    private Long USCOIDUS;

    @Column(name = "USCODOCU")
    private String USCODOCU;

    @Column(name = "USCONOMB")
    private String USCONOMB;

    @Column(name = "USCOEMAI")
    private String USCOEMAI;

    @Column(name = "USCOCODI")
    private String USCOCODI;

    @Column(name = "USCOESTA")
    private String USCOESTA;

    @Column(name = "USCOFECR")
    private LocalDateTime USCOFECR  ;
}
