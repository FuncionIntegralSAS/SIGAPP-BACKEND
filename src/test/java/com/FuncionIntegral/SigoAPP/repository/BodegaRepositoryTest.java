package com.FuncionIntegral.SigoAPP.repository;

import com.FuncionIntegral.SigoAPP.model.BodegaModel;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.namedparam.SqlParameterSource;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BodegaRepositoryTest {

    @Mock
    private NamedParameterJdbcTemplate jdbcTemplate;

    @InjectMocks
    private BodegaRepository repository;

    @Test
    void buscarPorDivision_RetornaBodegasActivas() {
        String divisionPrueba = "D001";
        BodegaModel mockModel = new BodegaModel();
        mockModel.setBodeCodi("BOD_01");
        mockModel.setBodeDesc("Principal");
        mockModel.setBodeDivi(divisionPrueba);
        mockModel.setBodeEsta("ac");

        when(jdbcTemplate.query(anyString(), any(SqlParameterSource.class), any(RowMapper.class)))
                .thenReturn(List.of(mockModel));

        List<BodegaModel> bodegas = repository.buscarPorDivision(divisionPrueba);

        assertNotNull(bodegas);
        assertFalse(bodegas.isEmpty(), "Debería retornar bodegas para la división");

        BodegaModel primeraBodega = bodegas.get(0);
        assertNotNull(primeraBodega.getBodeCodi());
        assertNotNull(primeraBodega.getBodeDesc());
        assertEquals(divisionPrueba, primeraBodega.getBodeDivi());
        assertEquals("ac", primeraBodega.getBodeEsta());
    }
}