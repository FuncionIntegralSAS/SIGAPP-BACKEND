package com.finte.sigapp.controller;

import com.finte.sigapp.dto.response.PersonResponse;
import com.finte.sigapp.model.PersonalModel;
import com.finte.sigapp.service.PersonalService;
import lombok.RequiredArgsConstructor;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/personal")
@RequiredArgsConstructor
public class PersonalController {

    private final PersonalService personalService;

    @GetMapping("/buscar")
    public ResponseEntity<List<PersonResponse>> buscarPersonal(
            @RequestParam(required = false) String nombre,
            @RequestParam(required = false) String apellido,
            @RequestParam(required = false) String cedula) {
        List<PersonResponse> resultados = personalService.buscarPorCriterios(nombre, apellido, cedula);
        return ResponseEntity.ok(resultados);
    }
}