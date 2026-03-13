package com.finte.sigapp.repository;

import com.finte.sigapp.model.SigappTraspasoModel;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.BeanPropertyRowMapper;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.core.simple.SimpleJdbcCall;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.util.List;
import java.util.Map;

@Repository
@RequiredArgsConstructor
public class TransferRepository {

    private final NamedParameterJdbcTemplate namedJdbcTemplate;
    private final DataSource dataSource;

    // 1. Métod  para CREAR (Llama al SP)
    public String llamarProcedimientoCrear(
            String empresa,
            String tipoMov,
            String articulo,
            String placa,
            String personaDestino,
            String observacion,
            String usuarioCrea
    ) {
        SimpleJdbcCall jdbcCall = new SimpleJdbcCall(dataSource)
                .withCatalogName("PKGFISIGAPPTRASPASO")      // Nombre del Paquete
                .withProcedureName("prc_crear_solicitud") // Nombre del Procedimiento
                .withReturnValue(); // Indica que esperamos parámetros de salida

        // Mapeo exacto de argumentos del SP
        MapSqlParameterSource inParams = new MapSqlParameterSource()
                .addValue("p_sitrempr",empresa)
                .addValue("p_sitrtimo",tipoMov)
                .addValue("p_sitrarti",articulo)
                .addValue("p_sitrplac",placa)
                .addValue("p_sitrpede",personaDestino)
                .addValue("p_sitrobse",observacion)
                .addValue("p_sitrdirc",usuarioCrea);

        // Ejecución
        Map<String,Object> out = jdbcCall.execute(inParams);

        // Recuperar ID de retorno (Asumiendo que el SP devuelve 'p_id_retorno')
        Object resultado = out.get("p_sitridtr");

        return resultado != null ? resultado.toString() : null;
    }

    public List<SigappTraspasoModel> listarPorBodegaYEstados(String bodegaOrigen,List<String> estados) {
        // Tu lógica exacta llevada a SQL puro
        String sql = """
            SELECT
                t.TRAS_ID as trasId,
                t.SITRARTI as trasArti,
                t.TRAS_MOTI as trasMoti,
                t.TRAS_FECH as trasFech,
                t.TRAS_ESTA as trasEsta,
                t.TRAS_RECH as trasRech,

                (SELECT MAX(p.PERSCODI || ' - ' || p.PERSNOMB || ' ' || p.PERSAPEL)
                   FROM ACTIFIJO a
                   JOIN SRF_PERSONAL p ON p.PERSCODI = a.ACFIPERS
                  WHERE a.ACFIPLAC = t.SITRPLAC AND a.ACFIARTI = t.SITRARTI) as personaFuenteDesc,

                (SELECT MAX(b.BODECODI || ' - ' || b.BODEDESC)
                   FROM ACTIFIJO a
                   JOIN SRF_PERSONAL p ON p.PERSCODI = a.ACFIPERS
                   JOIN SRF_BODEGA b ON b.BODEDIVI = p.PERSDIVI AND b.BODETIBO = 'PE'
                  WHERE a.ACFIPLAC = t.SITRPLAC AND a.ACFIARTI = t.SITRARTI) as bodegaFuenteDesc,

                (SELECT MAX(b.BODECODI || ' - ' || b.BODEDESC)
                   FROM SRF_PERSONAL p
                   JOIN SRF_BODEGA b ON b.BODEDIVI = p.PERSDIVI AND b.BODETIBO = 'PE'
                  WHERE p.PERSCODI = t.SITRPEDE) as bodegaDestinoDesc,

                (SELECT p.PERSCODI || ' - ' || p.PERSNOMB || ' ' || p.PERSAPEL
                   FROM SRF_PERSONAL p
                  WHERE p.PERSCODI = t.SITRPEDE) as personaDestinoDesc

              FROM SRF_FI_SIGATRAS t
             WHERE t.TRAS_BODE_ORI = :bodega
               AND t.TRAS_ESTA IN (:estados)
             ORDER BY t.TRAS_FECH DESC
        """;

        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("bodega",bodegaOrigen)
                .addValue("estados",estados);

        return namedJdbcTemplate.query(
                sql,
                params,
                new BeanPropertyRowMapper<>(SigappTraspasoModel.class)
        );
    }

    // 3. Mét odo para APROBAR / RECHAZAR
    public void callApproveRejectProcedure(
            Long idTramite,
            String estado,
            String observacion,
            String usuarioActualiza
    ) {
        SimpleJdbcCall jdbcCall = new SimpleJdbcCall(dataSource)
                .withCatalogName("PKGFISIGAPPTRASPASO")
                .withProcedureName("pro_aprobar_rechazar");

        MapSqlParameterSource inParams = new MapSqlParameterSource()
                .addValue("p_id_tramite",idTramite)
                .addValue("p_estado",estado)
                .addValue("p_observacion",observacion)
                .addValue("p_usuario_actualiza",usuarioActualiza);
        jdbcCall.execute(inParams);
    }
}