package com.finte.sigapp.repository;

import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.Optional;
import com.finte.sigapp.model.UsabadaModel;

@Repository
public class UsabadaRepository {
    private final JdbcTemplate jdbcTemplate;

    public UsabadaRepository(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    public Optional<UsabadaModel> buscarPorUsername(String username) {
        String sql = "SELECT * FROM USABADA WHERE USBDCODI = ?";
        var resultado = jdbcTemplate.query(sql, new BeanPropertyRowMapper<>(UsabadaModel.class), username);
        return resultado.isEmpty() ? Optional.empty() : Optional.of(resultado.get(0));
    }
}