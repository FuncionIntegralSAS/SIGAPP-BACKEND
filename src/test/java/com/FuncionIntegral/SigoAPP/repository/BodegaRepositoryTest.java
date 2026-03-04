package com.FuncionIntegral.SigoAPP.repository;

import com.FuncionIntegral.SigoAPP.model.BodegaModel;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
@ActiveProfiles("dev") // Opcional: Para usar application-dev.properties si tienes uno
@Transactional // MUY IMPORTANTE: Revierte los cambios de BD al finalizar el test
class BodegaRepositoryTest {

    @Autowired
    private BodegaRepository repository;

    @Test
    void buscarPorDivision_RetornaBodegasActivas() {
        // Nota: Para este test necesitas saber un código de división que realmente
        // exista en tu base de datos de pruebas (ej: 'D001').
        String divisionPrueba = "D001"; // CÁMBIALO por uno real de tu DB

        List<BodegaModel> bodegas = repository.buscarPorDivision(divisionPrueba);

        // Verificaciones
        assertNotNull(bodegas);
        // Si sabes que D001 tiene al menos 1 bodega, puedes hacer:
        assertFalse(bodegas.isEmpty(), "Debería retornar bodegas para la división");

        // Verificamos que los datos mapeen correctamente a las columnas
        BodegaModel primeraBodega = bodegas.get(0);
        assertNotNull(primeraBodega.getBodeCodi());
        assertNotNull(primeraBodega.getBodeDesc());
        assertEquals(divisionPrueba, primeraBodega.getBodeDivi());
        assertEquals("ac", primeraBodega.getBodeEsta());
    }
}