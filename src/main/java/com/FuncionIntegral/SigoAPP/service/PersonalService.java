package com.FuncionIntegral.SigoAPP.service;

import com.FuncionIntegral.SigoAPP.dto.response.PersonResponse;

import java.util.List;

public interface PersonalService {
    List<PersonResponse> buscarPorCriterios(String nombre, String apellido, String cedula);
}