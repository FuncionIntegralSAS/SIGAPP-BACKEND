package com.finte.sigapp.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ContarboPK implements Serializable {
    private String coabEmpr;
    private String coabBode;
    private String coabBolo;
    private LocalDateTime coabFech;
    private String coabArti;
    private String coabPlac;
}
