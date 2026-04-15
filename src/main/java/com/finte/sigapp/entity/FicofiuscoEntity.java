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
    @Column(name = "USCOIDUS", columnDefinition = "Identificador único usuario conteo")
    private Long USCOIDUS;

    @Column(name = "USCODOCU", columnDefinition = "documento del usuario")
    private String USCODOCU;

    @Column(name = "USCONOMB", columnDefinition = "Nombre usuario")
    private String USCONOMB;

    @Column(name = "USCOEMAI", columnDefinition = "email usuario")
    private String USCOEMAI;

    @Column(name = "USCOCODI", columnDefinition = "Código asignado para validación")
    private String USCOCODI;

    @Column(name = "USCOESTA", columnDefinition = "Estado ACTIVO, INACTIVO, BLOQUEADO")
    private String USCOESTA;

    @Column(name = "USCOFECR", columnDefinition = "fecha creación")
    private LocalDateTime USCOFECR  ;
}
