package com.finte.sigapp.service.impl;

import com.finte.sigapp.dto.response.PersonResponse;
import com.finte.sigapp.model.PersonalModel;
import com.finte.sigapp.repository.PersonalRepository;
import com.finte.sigapp.service.PersonalService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PersonalServiceImpl implements PersonalService {

    private final PersonalRepository repository;

    @Override
    public List<PersonResponse> buscarPorCriterios(String nombre, String apellido, String cedula) {
        // 1. VALIDACIÓN: Al menos uno debe tener texto
        if (esVacio(nombre) && esVacio(apellido) && esVacio(cedula)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "Debe enviar al menos un parámetro de búsqueda (nombre, apellido o cédula)"
            );
        }

        // 2. Llamada al repositorio
        return repository.buscarDinamica(nombre, apellido, cedula).stream()
                .map(this::mapToResponse)
                .collect(Collectors.toList());
    }

    // Métod auxiliar para validar nulos o vacíos
    private boolean esVacio(String str) {
        return str == null || str.trim().isEmpty();
    }

    // Mapper interno (o puedes usar tu clase PersonMapper)
    private PersonResponse mapToResponse(PersonalModel model) {
        return PersonResponse.builder()
                .id(model.getPersCodi()) // ID interno
                .nationalId(model.getPersDoid()) // Cédula (INT en Flutter)
                .fullName(model.getPersNomb() + " " + model.getPersApel())
                .divisionId(model.getPersDivi()) // <--- EL DATO CLAVE
                .isActive("ac".equalsIgnoreCase(model.getPersEsta()))
                .build();
    }
}