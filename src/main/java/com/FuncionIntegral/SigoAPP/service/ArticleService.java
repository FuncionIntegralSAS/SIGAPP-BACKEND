package com.FuncionIntegral.SigoAPP.service;


import com.FuncionIntegral.SigoAPP.dto.response.ArticleResponse;

import java.util.List;

public interface ArticleService {
    List<ArticleResponse> obtenerAsignados(String idResponsable, String idBodega);
}