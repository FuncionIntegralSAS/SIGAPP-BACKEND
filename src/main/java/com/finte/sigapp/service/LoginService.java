package com.finte.sigapp.service;

import com.finte.sigapp.dto.request.LoginRequest;
import com.finte.sigapp.dto.response.AuthResponse;

public interface LoginService {
    AuthResponse login(LoginRequest request);
}
