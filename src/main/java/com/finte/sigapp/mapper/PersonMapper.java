package com.finte.sigapp.mapper;

import com.finte.sigapp.dto.response.PersonResponse;
import com.finte.sigapp.model.PersonalModel;
import org.springframework.stereotype.Component;

@Component
public class PersonMapper {
    public PersonResponse toResponse(PersonalModel model) {
        if (model == null) return null;
        return PersonResponse.builder()
                .nationalId(model.getPersDoid())
                .fullName(model.getPersNomb() + " " + model.getPersApel())
                .isActive("ac".equalsIgnoreCase(model.getPersEsta()))
                .accountExists(model.getPersUsua() != null)
                .build();
    }
}