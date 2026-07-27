package com.finte.sigapp.repository;

import com.finte.sigapp.dto.response.ProcedureResultResponse;
import com.finte.sigapp.utils.ProcedureExecutor;
import com.finte.sigapp.utils.ProcedureParam;
import jakarta.persistence.ParameterMode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Slf4j
@Repository
@RequiredArgsConstructor
public class CorreoRepository {

    private final ProcedureExecutor procedureExecutor;

    public ProcedureResultResponse enviarCodigo(String correo, String codigo) {
        log.info("Enviando correo a: {}", correo);

        List<ProcedureParam> params = List.of(
                new ProcedureParam("isbdestinatario", ParameterMode.IN, String.class, correo),
                new ProcedureParam("isbdestina_cc", ParameterMode.IN, String.class, null),
                new ProcedureParam("issbCodigo", ParameterMode.IN, String.class, codigo),
                new ProcedureParam("onuerrorid", ParameterMode.OUT, Long.class, null),
                new ProcedureParam("osberrorlog", ParameterMode.OUT, String.class, null));

        Map<String, Object> resultado = procedureExecutor
                .ejecutarProcedimientoConSalida("prc_envia_correo_sigapp", params);

        Long errorCode = resultado.get("onuerrorid") != null
                ? ((Number) resultado.get("onuerrorid")).longValue()
                : null;
        String errorLog = (String) resultado.get("osberrorlog");

        return new ProcedureResultResponse(errorCode, errorLog);
    }
}
