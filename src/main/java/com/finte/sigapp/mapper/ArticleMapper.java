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
                .artiCodi(model.getAcfiarti())
                .artiDesc(model.getAcfiobse())
                .artiPlac(model.getAcfiPlac())
                .build();
    }
}