package com.finte.sigapp.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Entity
@Table(name = "FI_COFIARAS")
public class FicofiarasEntity {
    @Id
    @Column(name = "ARASIDAS", columnDefinition = "Identificador asignación")
    private Long ARASIDAS;

    @Column(name = "ARASIDCO", columnDefinition = "Id conteo físico")
    private Long ARASIDCO;

    @Column(name = "ARASIDBO", columnDefinition = "Id bodega")
    private String ARASIDBO;

    @Column(name = "ARASIDAR", columnDefinition = "Id artículo sistema financiero")
    private String ARASIDAR;

    @Column(name = "ARASIDUS", columnDefinition = "Id usuario responsable")
    private Long ARASIDUS;

    @Column(name = "ARASNUCO", columnDefinition = "Número conteo: 1,2,3")
    private Integer ARASNUCO;

    @Column(name = "ARASCOQR", columnDefinition = "Código barras artículo")
    private String ARASCOQR;

    @Column(name = "ARASCANT", columnDefinition = "Cantidad contada")
    private Long ARASCANT;

    @Column(name = "ARASFECO", columnDefinition = "Fecha conteo")
    private LocalDateTime ARASFECO;

    @Column(name = "ARASEMPR", columnDefinition = "empresa")
    private String ARASEMPR;

    @Column(name = "ARASPLAC", columnDefinition = "placa del articulo")
    private String ARASPLAC;

    @Column(name = "ARASESTA", columnDefinition = "Estado: PENDIENTE, CONTADO, VALIDADO")
    private String ARASESTA;

    @Column(name = "ARASSINC",columnDefinition = "Sincronización: S,N")
    private String ARASSINC;

    @Column(name = "ARASFESI", columnDefinition = "Fecha Sincronización")
    private LocalDateTime ARASFESI;
}
