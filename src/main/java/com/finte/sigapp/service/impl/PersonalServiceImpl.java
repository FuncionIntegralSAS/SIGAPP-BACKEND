package com.finte.sigapp.service.impl;

import com.finte.sigapp.dto.response.PersonResponse;
import com.finte.sigapp.mapper.PersonMapper;
import com.finte.sigapp.repository.PersonalRepository;
import com.finte.sigapp.service.PersonalService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class PersonalServiceImpl implements PersonalService {

    private final PersonalRepository repository;
    private final PersonMapper personMapper;

    @Override
    public List<PersonResponse> buscarPorCriterios(String nombre, String apellido, String cedula) {
        return repository.buscarDinamica(nombre, apellido, cedula)
                .stream()
                .map(personMapper::toResponse)
                .collect(Collectors.toList());
    }

}