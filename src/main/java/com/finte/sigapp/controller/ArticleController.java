package com.finte.sigapp.controller;

import com.finte.sigapp.dto.response.ArticleResponse;
import com.finte.sigapp.service.ArticleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/articulos")
@RequiredArgsConstructor
public class ArticleController {

    private final ArticleService articleService;

    @GetMapping("/asignados")
    public ResponseEntity<List<ArticleResponse>> getAssignedArticles(
            @RequestParam(name = "responsable") String idResponsable, // Obligatorio
            @RequestParam(name = "bodega", required = false) String idBodega // Opcional
    ) {

        List<ArticleResponse> articulos = articleService.obtenerAsignados(idResponsable, idBodega);

        if (articulos.isEmpty()) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(articulos);
    }

    @GetMapping("/asignados/{bodega}/{empresa}")
    public ResponseEntity<List<ArticleResponse>> getAssignedArticlesByBodega(
            @PathVariable(name = "bodega") String bodega,
            @PathVariable(name = "empresa") String empresa) {

        List<ArticleResponse> articulos = articleService.obtenerAsignadosPorBodegaAndEmpresa(bodega, empresa);

        if (articulos.isEmpty()) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(articulos);
    }

}