package com.finte.sigapp.service;

import com.finte.sigapp.dto.request.AsignacionConteoRequest;
import com.finte.sigapp.entity.FicofiuscoEntity;

import java.util.List;

public interface FicofiuscoService {

    List<FicofiuscoEntity> procesarUsuarios(AsignacionConteoRequest request);
}
