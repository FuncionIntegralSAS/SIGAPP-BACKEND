package com.finte.sigapp.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

@Data
@Entity
@Table
public class PersonalEntity {
    @Id
    @Column(name ="PERSCODI")
    private String perscodi;
    @Column(name ="PERSNOMB")
    private String persnomb;
    @Column(name ="PERSAPEL")
    private String persapel;
    @Column(name ="PERSTIPE")
    private String perstipe;
    @Column(name ="PERSCARG")
    private String perscarg;
    @Column(name ="PERSTELE")
    private String perstele;
    @Column(name ="PERSCIUD")
    private String persciud;
    @Column(name ="PERSDIRE")
    private String persdire;
    @Column(name ="PERSDIEM")
    private String persdiem;
    @Column(name ="PERSDIVI")
    private String persdivi;
    @Column(name ="PERSUSUA")
    private String persusua;
    @Column(name ="PERSESTA")
    private String persesta;
    @Column(name ="PERSCLAV")
    private String persclav;
    @Column(name ="PERSCOEL")
    private String perscoel;
    @Column(name ="PERSFIEL")
    private String persfiel;
    @Column(name ="PERSBOCO")
    private String persboco;
    @Column(name ="PERSIDEN")
    private String persiden;
    @Column(name ="PERSCIEX")
    private String persciex;
    @Column(name ="PERSSEPE")
    private String perssepe;
}
