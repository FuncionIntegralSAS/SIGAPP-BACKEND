package com.finte.sigapp.service;

public interface EmailService {
    void enviarCodigoAcceso(String destino, String codigo);
}
