package com.finte.sigapp.repository;

import com.finte.sigapp.model.BodegaModel;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class BodegaRepository  {
  // todo: Se debe pasar a JPA Repository.
  private final NamedParameterJdbcTemplate namedJdbcTemplate;

  public List<BodegaModel> buscarPorDivision(String codigoDivision) {
    String sql = """
            SELECT BODECODI, BODENOMB, BODEDIVI, BODEESTA
              FROM BODEGA
             WHERE BODEESTA = 'ac'
               AND BODEDIVI = :divi
               AND BODETIBO = 'PE'
             ORDER BY BODENOMB ASC
        """;

    MapSqlParameterSource params = new MapSqlParameterSource()
        .addValue("divi", codigoDivision);

    return namedJdbcTemplate.query(
        sql,
        params,
        new BeanPropertyRowMapper<>(BodegaModel.class));
  }
}