package com.finte.sigapp.service;

import com.finte.sigapp.entity.FicofiuscoEntity;

import java.util.List;

public interface EmailService {
    void enviarCodigoAcceso(List<FicofiuscoEntity> usuarios);
}