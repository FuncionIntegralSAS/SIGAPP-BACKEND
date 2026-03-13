package com.finte.sigapp.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PersonalModel {
    private String persCodi;
    private String persNomb;
    private String persApel;
    private String persTipe;
    private String persCarg;
    private String persTele;
    private String persEmail;
    private String persDire;
    private String persEsta;
    private LocalDateTime persFech;
    private String persDoid;
    private String persUsua;
    private String persDivi;
}