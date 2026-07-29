package com.finte.sigapp.repository;

import com.finte.sigapp.utils.ProcedureExecutor;
import com.finte.sigapp.utils.ProcedureParam;
import jakarta.persistence.ParameterMode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.Date;
import java.util.List;

@Slf4j
@Repository
@RequiredArgsConstructor
public class ConteoFisicoRepository {

    private final ProcedureExecutor procedureExecutor;

    /**
     * Llama al procedimiento PL/SQL PKGCONTARBO.proGeneContFisi
     * para generar el conteo físico.
     */
    public void generarConteoFisico(String empresa,
            String bodega,
            String bodegaLogica,
            String articulo,
            LocalDateTime fecha,
            String verificarExistencia) {

        log.info(
                "Llamando a proGeneContFisi - Empresa: {}, Bodega: {}, Bodega Lógica: {} Artículo: {}, Fecha: {}, Verificar Existencia: {}",
                empresa, bodega, bodegaLogica, articulo, fecha, verificarExistencia);

        // seteo parámetros del procedimiento
        List<ProcedureParam> params = List.of(
                new ProcedureParam("P_sbEmpr", ParameterMode.IN, String.class, empresa),
                new ProcedureParam("P_sbBode", ParameterMode.IN, String.class, bodega),
                new ProcedureParam("P_sbBolo", ParameterMode.IN, String.class, bodegaLogica),
                new ProcedureParam("P_sbArti", ParameterMode.IN, String.class, articulo),
                new ProcedureParam("P_dtFech", ParameterMode.IN, Date.class, fecha),
                new ProcedureParam("P_sbVaex", ParameterMode.IN, String.class, verificarExistencia));
        procedureExecutor.ejecutarProcedimiento("PKGCONTARBO.proGeneContFisi",
                params);
    }

    /**
     * Invoca la función PL/SQL PKGCONTARBO.fun_ValidaBodega para verificar si la
     * bodega ya se encuentra en conteo físico.
     *
     * @return true si la bodega ya está en conteo, false si no lo está.
     */
    public boolean validarBodegaEnConteo(String empresa, String bodega,
            String bodegaLogica, LocalDateTime fecha) {

        log.info("Validando bodega en conteo - Empresa: {}, Bodega: {}, Bodega Lógica: {}, Fecha: {}",
                empresa, bodega, bodegaLogica, fecha);

        List<ProcedureParam> params = List.of(
                new ProcedureParam("sbCoabEmpr", ParameterMode.IN, String.class, empresa),
                new ProcedureParam("sbCoabBode", ParameterMode.IN, String.class, bodega),
                new ProcedureParam("sbCoabBolo", ParameterMode.IN, String.class, bodegaLogica),
                new ProcedureParam("dtCoabFech", ParameterMode.IN, LocalDateTime.class, fecha));

        return procedureExecutor.ejecutarFuncion("PKGCONTARBO.fun_ValidaBodega",
                Boolean.class,
                params);
    }

}
