package com.finte.sigapp.service;


import com.finte.sigapp.dto.response.ArticleResponse;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ArticleService {
    List<ArticleResponse> obtenerAsignados(String idResponsable, String idBodega);
}