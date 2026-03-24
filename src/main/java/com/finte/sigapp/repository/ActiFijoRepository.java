package com.finte.sigapp.repository;

import com.finte.sigapp.model.ActiFijoModel;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface ActiFijoRepository extends JpaRepository<ActiFijoModel,Long> {

    @Query(value = """
    SELECT * 
     FROM actifijo
    WHERE ACFIPERS = :responsable
      AND ACFIESTA = 'ac'
      AND (:bodega IS NULL OR ACFIBODE = :bodega)            
    """, nativeQuery = true)
    List<ActiFijoModel> buscarAsignados(@Param("responsable") String responsable,
                                        @Param("bodega") String bodega);
}