package com.finte.sigapp.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "EMPRESA")
public class EmpresaEntity {

    @Id
    @Column(name = "EMPRCODI", length = 6, nullable = false)
    private String emprCodi;

    @Column(name = "EMPRDESC",length = 40)
    private String emprDesc;

    @Column(name = "EMPRNIT")
    private String emprNit;

    @Column(name = "EMPRESTA", length = 2, nullable = false)
    private String emprEsta;
}
