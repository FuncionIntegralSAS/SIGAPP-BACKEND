package com.finte.sigapp.service.impl;

import com.finte.sigapp.dto.response.ProcedureResultResponse;
import com.finte.sigapp.entity.FicofiuscoEntity;
import com.finte.sigapp.repository.CorreoRepository;
import com.finte.sigapp.service.EmailService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class EmailServiceImpl implements EmailService {

    private final CorreoRepository correoRepository;

    @Value("${app.features.envio-correo}")

    private boolean envioCorreoHabilitado;
    @Override
    public void enviarCodigoAcceso(List<FicofiuscoEntity> usuarios){
        if (!envioCorreoHabilitado){
            log.info("[DEV] envio deshabilitado - {} usuarios omitidos.", usuarios.size());
            return;
        }

        usuarios.forEach(this::procesarEnvio);
    }

    private void procesarEnvio(FicofiuscoEntity usuario){
        try {
            ProcedureResultResponse resultado = correoRepository
                    .enviarCodigo(usuario.getUSCOEMAI(), usuario.getUSCOCODI());

            if (resultado.getErrorId() == 0L){
                log.info("Correo enviado a '{}'", usuario.getUSCOEMAI());
            }else{
                log.warn("Oracle reportó fallo para '{}': {}",
                        usuario.getUSCOEMAI(), resultado.getErrorLog());
            }
        }catch (Exception e){
            log.error("Error técnico enviando correo a '{}' : {}",
                    usuario.getUSCOEMAI(), e.getMessage(),e);
        }
    }
}
