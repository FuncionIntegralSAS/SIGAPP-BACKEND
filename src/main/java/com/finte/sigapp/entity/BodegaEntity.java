package com.finte.sigapp.entity;

import jakarta.persistence.*;
import lombok.Data;
import lombok.NoArgsConstructor;
import lombok.AllArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "BODEGA")
@IdClass(BodegaPK.class)
public class BodegaEntity {

    @Id
    @Column(name = "BODEEMPR",nullable = false, length = 4)
    private String bodeEmpr;

    @Id
    @Column(name = "BODECODI", nullable = false, length = 6)
    private String bodeCodi;


    @Column(name = "BODEDESC")
    private String bodeDesc;

    @Column(name = "BODEESTA")
    private String bodeEsta;

}
