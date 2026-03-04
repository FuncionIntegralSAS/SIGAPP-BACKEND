package com.FuncionIntegral.SigoAPP.mapper;

import com.FuncionIntegral.SigoAPP.dto.response.ArticleResponse;
import com.FuncionIntegral.SigoAPP.model.ActiFijoModel;
import org.springframework.stereotype.Component;

@Component
public class ArticleMapper {
    public ArticleResponse toResponse(ActiFijoModel model) {
        if (model == null) return null;
        return ArticleResponse.builder()
                .id(model.getAcfiArti())
                .name(model.getAcfiObse()) // Usamos la observación como nombre
                .licensePlate(model.getAcfiPlac())
                .warehouse(model.getAcfiBode())
                .status(model.getAcfiEsta())
                // Los campos GPS y fotos se manejarán en la lógica de negocio
                .build();
    }
}