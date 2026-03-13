package com.finte.sigapp.repository;

import com.finte.sigapp.utils.ProcedureExecutor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.sql.Types;
import java.util.Date;

@Slf4j
@Repository
@RequiredArgsConstructor
public class ConteoFisicoRepository {

    private static final String PACKAGE = "PKGCONTARBO";

    private final ProcedureExecutor procedureExecutor;

    /**
     * Llama al procedimiento PL/SQL PKGCONTARBO.proGeneContFisi para generar el
     * conteo físico.
     */
    public void llamarProcedimientoConteoFisico(
            String empresa,
            String bodega,
            String bodegaLogica,
            String articulo,
            Date fecha,
            String verificarExistencia) {

        log.info("Llamando a proGeneContFisi - Empresa: {}, Bodega: {}, Bodega Lógica: {}, "
                + "Artículo: {}, Fecha: {}, Verificar Existencia: {}",
                empresa, bodega, bodegaLogica, articulo, fecha, verificarExistencia);

        // TODO: descomentar para produccion y production-test
        /*
         * procedureExecutor.ejecutarProcedimiento(PACKAGE, "proGeneContFisi",
         * new Object[] { "P_sbEmpr", empresa, String.class },
         * new Object[] { "P_sbBode", bodega, String.class },
         * new Object[] { "P_sbBolo", bodegaLogica, String.class },
         * new Object[] { "P_sbArti", articulo, String.class },
         * new Object[] { "P_dtFech", fecha, Date.class },
         * new Object[] { "P_sbVaex", verificarExistencia, String.class });
         */
    }

    /**
     * Invoca la función PL/SQL PKGCONTARBO.fun_ValidaBodega para verificar si la
     * bodega
     * ya se encuentra en conteo físico.
     *
     * @return true si la bodega ya está en conteo, false si no lo está.
     */
    public boolean validarBodegaEnConteo(
            String empresa,
            String bodega,
            String bodegaLogica,
            Date fecha) {

        log.info("Validando bodega en conteo - Empresa: {}, Bodega: {}, Bodega Lógica: {}, Fecha: {}",
                empresa, bodega, bodegaLogica, fecha);

        return procedureExecutor.ejecutarFuncion(PACKAGE,
                "fun_ValidaBodega",
                Boolean.class,
                new Object[] { empresa, bodega, bodegaLogica, fecha },
                new int[] { Types.VARCHAR, Types.VARCHAR, Types.VARCHAR, Types.DATE });

        // Retorno temporal hasta habilitar conexión a base de datos
        // return false;
    }
}
