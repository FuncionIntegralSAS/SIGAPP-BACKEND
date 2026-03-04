package com.FuncionIntegral.SigoAPP.controller;

import com.FuncionIntegral.SigoAPP.dto.response.BodegaResponse;
import com.FuncionIntegral.SigoAPP.service.BodegaService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/v1/bodegas")
@RequiredArgsConstructor
public class BodegaController {

    private final BodegaService bodegaService;

    // GET /api/v1/bodegas/division/D001
    @GetMapping("/division/{divisionId}")
    public ResponseEntity<List<BodegaResponse>> getByDivision(@PathVariable String divisionId) {
        List<BodegaResponse> bodegas = bodegaService.buscarPorDivision(divisionId);

        if (bodegas.isEmpty()) {
            return ResponseEntity.noContent().build();
        }
        return ResponseEntity.ok(bodegas);
    }
}