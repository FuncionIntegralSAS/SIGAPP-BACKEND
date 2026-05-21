package com.finte.sigapp.service.impl;

import com.finte.sigapp.service.BodegaService;
import com.finte.sigapp.service.EmailService;
import lombok.RequiredArgsConstructor;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class EmailServiceImpl implements EmailService {

    private final JavaMailSender mailSender;

    public void enviarCodigoAcceso(String destino, String codigo){

        SimpleMailMessage mensaje = new SimpleMailMessage();

        mensaje.setTo(destino);
        mensaje.setSubject("Codigo temporal de acceso SIGAPP conteo fisico");
        mensaje.setText("Su código temporal de acceso es: " + codigo);

        mailSender.send(mensaje);
    }
}
