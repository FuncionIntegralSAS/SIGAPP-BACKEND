package com.FuncionIntegral.SigoAPP.mapper;

import com.FuncionIntegral.SigoAPP.dto.response.PersonResponse;
import com.FuncionIntegral.SigoAPP.model.PersonalModel;
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