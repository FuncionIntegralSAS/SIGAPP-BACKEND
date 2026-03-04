package com.FuncionIntegral.SigoAPP.controller;

import com.FuncionIntegral.SigoAPP.dto.response.ArticleResponse;
import com.FuncionIntegral.SigoAPP.service.ArticleService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/articulos")
@RequiredArgsConstructor
public class ArticleController {

    private final ArticleService articleService;

    @GetMapping("/bodega/{idBodega}")
    public ResponseEntity<List<ArticleResponse>> getByWarehouse(@PathVariable String idBodega) {
        List<ArticleResponse> articulos = articleService.obtenerPorBodega(idBodega);

        if (articulos.isEmpty()) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(articulos);
    }

    @GetMapping("/placa/{placa}")
    public ResponseEntity<ArticleResponse> getByPlaca(@PathVariable String placa) {
        return ResponseEntity.ok(articleService.obtenerPorPlaca(placa));
    }

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
}