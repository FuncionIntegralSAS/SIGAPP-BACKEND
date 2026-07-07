package com.finte.sigapp.model;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Data;

@Data
@Entity
@Table(name = "PERSONAL")
public class PersonalModel {
    @Id
    private String perscodi;
    private String persnomb;
    private String persapel;
    private String perscoel;
    private String persdivi;
    private String persesta;
}