package com.finte.sigapp.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ContarboPK implements Serializable {
    private String coabEmpr;
    private String coabBode;
    private String coabBolo;
    private String coabFech;
    private Long coabArti;
    private String coabPlac;
}
