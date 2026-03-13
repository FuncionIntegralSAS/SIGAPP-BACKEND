package com.finte.sigapp.service;

import com.finte.sigapp.dto.response.PersonResponse;

import java.util.List;

public interface PersonalService {
    List<PersonResponse> buscarPorCriterios(String nombre, String apellido, String cedula);
}