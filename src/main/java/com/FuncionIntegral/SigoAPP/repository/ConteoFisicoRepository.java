package com.FuncionIntegral.SigoAPP.repository;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.simple.SimpleJdbcCall;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.util.Date;

@Repository
public class ConteoFisicoRepository {

    private final DataSource dataSource;

    public ConteoFisicoRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void llamarProcedimientoConteoFisico(
            String empresa,
            String bodega,
            String bolo,
            String articulo,
            Date fecha,
            String vaex
    ) {
        SimpleJdbcCall jdbcCall = new SimpleJdbcCall(dataSource)
                .withCatalogName("PKGCONTARBO")
                .withProcedureName("proGeneContFisi");

        MapSqlParameterSource inParams = new MapSqlParameterSource()
                .addValue("P_sbEmpr", empresa)
                .addValue("P_sbBode", bodega)
                .addValue("P_sbBolo", bolo)
                .addValue("P_sbArti", articulo)
                .addValue("P_dtFech", fecha)
                .addValue("P_sbVaex", vaex);

        jdbcCall.execute(inParams);
    }
}
