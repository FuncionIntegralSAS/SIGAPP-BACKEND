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

    private static final String BLOQUE_ENVIO_CORREO = """
            DECLARE 
                v_error NUMBER;
                v_log   VARCHAR2(4000);
                v_mensaje CLOB :=  TO_CLOB(?);
                v_limpiar BOOLEAN := CASE ? WHEN 1 THEN TRUE ELSE FALSE END;
            BEGIN
                INT_PKMAILAPI.prc_EnviaCorreo(
                    isbDestinatario => ?,
                    isbDestina_CC   => ?,
                    isbAsunto       => ?,
                    iclmensaje      => v_mensaje,
                    iboolLimpiar    => v_limpiar,
                    onuerrorid      => v_error,
                    osbErrorlog     => v_log
                );
                ? := v_error;
                ? := v_log;
            END;
            """;
    public ProcedureResultResponse enviarCodigo(String correo, String codigo){
        log.info("Enviando correo a: {}",correo);

        String asunto = "Código temporal acceso SIGAPP";
        String mensaje = "<h2>Conteo Físico</h2>"
                        + "<p>Su código temporal es:</p>"
                        + "<h1>" + codigo + "</h1>";

        List<ProcedureParam> paramsIn = List.of(
                // pos 1: mensaje como String → el bloque lo convierte con TO_CLOB
                new ProcedureParam("v_mensaje", ParameterMode.IN,  String.class, mensaje),
                // pos 2: iboolLimpiar → FALSE = 0
                new ProcedureParam("v_limpiar", ParameterMode.IN,Integer.class, 0),
                // pos 3, 4, 5: parámetros del procedimiento
                new ProcedureParam("isbDestinatario",  ParameterMode.IN,  String.class,correo),
                new ProcedureParam("isbDestina_CC", ParameterMode.IN,    String.class, null),
                new ProcedureParam("isbAsunto", ParameterMode.IN, String.class, asunto)
        );

        List<ProcedureParam> paramsOut = List.of(
                new ProcedureParam("v_error", ParameterMode.OUT, Long.class, null),
                new ProcedureParam("v_log", ParameterMode.OUT,  String.class, null)
        );

        Map<String,Object> resultado = procedureExecutor
                .ejecutarBloqueConSalida(BLOQUE_ENVIO_CORREO,paramsIn,paramsOut);

        Long errorCode = resultado.get("v_error") != null
                ? ((Number) resultado.get("v_error")).longValue() : null;
        String errorLog = (String) resultado.get("v_log");

        return new ProcedureResultResponse(errorCode,errorLog);
    }
    public void enviarCodigo1(String correo,String codigo){


        String asunto = "Código temporal acceso SIGAPP";
        String mensaje = "<h2>Conteo Físico</h2>" +
                         "<p>Su código temporal es:</p>" +
                         "<h1>" + codigo + "</h1>";
        String bloque = """
               DECLARE
                   v_error NUMBER;
                   v_log   VARCHAR2(4000);
               BEGIN
                   
                   INT_PKMAILAPI.prc_EnviaCorreo(
                       isbDestinatario => :correo,
                       isbDestina_CC   => NULL,
                       isbAsunto       => :asunto,
                       iclmensaje      => :mensaje,
                       onuerrorid      => v_error,
                       osbErrorlog     => v_log
                   );
                   
                   IF v_error <> 0 THEN
                       RAISE_APPLICATION_ERROR(
                           -20001,
                           NVL(v_log,'Error enviando correo')
                       );
                   END IF;
                   
               END;
    """;

        Map<String,Object> params = Map.of(
                "correo", correo,
                "asunto",asunto,
                "mensaje", mensaje
        );

        procedureExecutor.ejecutarBloqueAnonimo(bloque,params);
    }



}
