package com.finte.sigapp.mapper;

import com.finte.sigapp.dto.response.PersonResponse;
import com.finte.sigapp.model.PersonalModel;
import org.springframework.stereotype.Component;

@Component
public class PersonMapper {
    public PersonResponse toResponse(PersonalModel model) {
        if (model == null)
            return null;
        return PersonResponse.builder()
                .nombre(model.getPersnomb())
                .apellido(model.getPersapel())
                .cedula(model.getPerscodi())
                .estado(model.getPersesta())
                .division(model.getPersdivi())
                .correo(model.getPerscoel())
                .build();
    }
}