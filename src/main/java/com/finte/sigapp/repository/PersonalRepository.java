package com.finte.sigapp.repository;

import com.finte.sigapp.model.PersonalModel;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class PersonalRepository {

    private final NamedParameterJdbcTemplate namedJdbcTemplate;

    public List<PersonalModel> buscarDinamica(String nombre, String apellido, String cedula) {
        // 1. SQL Base: Siempre filtramos que esté activo
        StringBuilder sql = new StringBuilder("""
            SELECT PERSCODI, PERSNOMB, PERSAPEL, PERSDOID, PERSDIDI
              FROM PERSONAL
             WHERE PERSESTA = 'ac'
            """
        );

        MapSqlParameterSource params = new MapSqlParameterSource();

        if (nombre != null && !nombre.isEmpty()) {
            sql.append(" AND UPPER(PERSNOMB) LIKE UPPER(:nombre) ");
            params.addValue("nombre", "%" + nombre + "%");
        }

        if (apellido != null && !apellido.isEmpty()) {
            sql.append(" AND UPPER(PERSAPEL) LIKE UPPER(:apellido) ");
            params.addValue("apellido", "%" + apellido + "%");
        }

        if (cedula != null && !cedula.isEmpty()) {
            // La cédula suele buscarse exacta o por inicio, aquí usaremos LIKE por flexibilidad
            sql.append(" AND PERSDOID LIKE :cedula ");
            params.addValue("cedula", "%" + cedula + "%");
        }

        return namedJdbcTemplate.query(
                sql.toString(),
                params,
                new BeanPropertyRowMapper<>(PersonalModel.class)
        );
    }
}