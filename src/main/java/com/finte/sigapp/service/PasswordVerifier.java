package com.finte.sigapp.service;

public interface PasswordVerifier {
    boolean matches(String rawPassword, String storedPassword);
}
