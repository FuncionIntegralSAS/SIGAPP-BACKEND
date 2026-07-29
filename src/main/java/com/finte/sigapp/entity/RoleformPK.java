package com.finte.sigapp.entity;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class RoleformPK implements Serializable {
    private String roforole;
    private String rofoform;
}
