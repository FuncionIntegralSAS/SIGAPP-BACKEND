package com.finte.sigapp.repository;

import com.finte.sigapp.model.ActiFijoModel;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class ActiFijoRepository {

    private final NamedParameterJdbcTemplate namedJdbcTemplate;

    public ActiFijoRepository(NamedParameterJdbcTemplate namedJdbcTemplate) {
        this.namedJdbcTemplate = namedJdbcTemplate;
    }

    public List<ActiFijoModel> buscarAsignados(String idResponsable, String idBodega) {
        String sql = """
                    SELECT ACFIARTI, ACFIPLAC, ACFIBODE, ACFIPERS,
                           ACFIOBSE, ACFINUSE, ACFIESTA, ACFIESAC, ACFICOAC
                      FROM ACTIFIJO
                     WHERE ACFIPERS = :responsable
                       AND ACFIESTA = 'ac'
                       AND (:bodega IS NULL OR ACFIBODE = :bodega)
                """;

        // Mapeo simple de parámetros
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("responsable", idResponsable)
                .addValue("bodega", idBodega); // Si llega null, Spring lo pasa como NULL a Oracle

        return namedJdbcTemplate.query(
                sql,
                params,
                new BeanPropertyRowMapper<>(ActiFijoModel.class));
    }
}