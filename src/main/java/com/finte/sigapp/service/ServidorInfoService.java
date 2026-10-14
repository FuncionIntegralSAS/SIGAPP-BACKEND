package com.finte.sigapp.service;

import com.finte.sigapp.dto.response.ObjectResponse;

public interface ServidorInfoService {

    /**
     * Identidad del backend: aplicacion y URL publica configurada.
     */
    ObjectResponse consultarInfo();
}
