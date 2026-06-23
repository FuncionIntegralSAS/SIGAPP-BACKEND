package com.finte.sigapp.service;

import com.finte.sigapp.dto.request.LoginContadorRequest;
import com.finte.sigapp.dto.response.AuthResponse;

public interface ContadorAuthService {
    AuthResponse loginContador(LoginContadorRequest request);

    AuthResponse refreshContador(String refreshToken);

}
