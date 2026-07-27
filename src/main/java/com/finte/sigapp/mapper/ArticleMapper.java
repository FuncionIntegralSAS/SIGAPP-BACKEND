package com.finte.sigapp.mapper;

import com.finte.sigapp.dto.response.ArticleResponse;
import com.finte.sigapp.model.ActiFijoModel;
import org.springframework.stereotype.Component;

@Component
public class ArticleMapper {
    public ArticleResponse toResponse(ActiFijoModel model) {
        if (model == null)
            return null;
        return ArticleResponse.builder()
                .id(model.getAcfiarti())
                .name(model.getAcfiobse()) // Usamos la observación como nombre
                // .licensePlate(model.getAcfiPlac())
                // .warehouse(model.getAcfiBode())
                // .status(model.getAcfiEsta())
                // Los campos GPS y fotos se manejarán en la lógica de negocio
                .build();
    }
}