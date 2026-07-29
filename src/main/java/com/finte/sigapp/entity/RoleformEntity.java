package com.finte.sigapp.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "ROLEFORM")
@IdClass(RoleformPK.class)
public class RoleformEntity {

    @Id
    @Column(name = "ROFOROLE")
    private String roforole;
    @Id
    @Column(name = "ROFOFORM")
    private String rofoform;
    @Column(name = "ROFOTIRO")
    private String rofotiro;
    @Column(name = "ROFOTIFO")
    private String rofotifo;
    @Column(name = "ROFOPROD")
    private String rofoprod;
}
