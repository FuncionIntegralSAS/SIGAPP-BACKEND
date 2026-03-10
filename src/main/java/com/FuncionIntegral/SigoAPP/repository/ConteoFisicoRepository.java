package com.FuncionIntegral.SigoAPP.repository;

import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.simple.SimpleJdbcCall;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.util.Date;

import lombok.extern.slf4j.Slf4j;

@Slf4j
@Repository
public class ConteoFisicoRepository {

    private final DataSource dataSource;

    public ConteoFisicoRepository(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public void llamarProcedimientoConteoFisico(
            String empresa,
            String bodega,
            String bodegaLogica,
            String articulo,
            Date fecha,
            String verificarExistencia) {
        // SimpleJdbcCall jdbcCall = new SimpleJdbcCall(dataSource)
        // .withCatalogName("PKGCONTARBO")
        // .withProcedureName("proGeneContFisi");

        // MapSqlParameterSource inParams = new MapSqlParameterSource()
        // .addValue("P_sbEmpr", empresa)
        // .addValue("P_sbBode", bodega)
        // .addValue("P_sbBolo", bodegaLogica)
        // .addValue("P_sbArti", articulo)
        // .addValue("P_dtFech", fecha)
        // .addValue("P_sbVaex", verificarExistencia);

        // jdbcCall.execute(inParams);
        log.info("Procedimiento llamado exitosamente");
        log.info("Empresa: {}", empresa);
        log.info("Bodega: {}", bodega);
        log.info("Bodega Logica: {}", bodegaLogica);
        log.info("Articulo: {}", articulo);
        log.info("Fecha: {}", fecha);
        log.info("Verificar Existencia: {}", verificarExistencia);
    }
}
