package com.finte.sigapp.utils;

import jakarta.persistence.ParameterMode;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@AllArgsConstructor
public class ProcedureParam {
    private String name;
    private ParameterMode mode;
    private Class<?> javaType;
    private Object value;



}
