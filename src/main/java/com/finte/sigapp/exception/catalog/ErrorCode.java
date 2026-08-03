package com.finte.sigapp.exception.catalog;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum ErrorCode {
    // Errores de negocio
    SIGAPP_001("SIGAPP_001", "Numero de conteo invalido"),
    SIGAPP_002("SIGAPP_002", "El usuario no tiene articulos pendientes"),
    SIGAPP_003("SIGAPP_003", "Existen articulos no asignados al usuario"),
    SIGAPP_004("SIGAPP_004", "La lista de usuarios esta vacia"),
    SIGAPP_005("SIGAPP_005", "La lista de articulos esta vacia"),
    SIGAPP_006("SIGAPP_006", "Fecha invalida"),
    SIGAPP_007("SIGAPP_007", "Usuario no encontrado"),
    SIGAPP_008("SIGAPP_008", "Usuario inactivo"),
    SIGAPP_009("SIGAPP_009", "Codigo temporal incorrecto"),
    // Errores de seguridad
    SIGAPP_400("SIGAPP_400", "Solicitud invalida"),
    SIGAPP_401("SIGAPP_401", "Usuario o contraseña incorrectos"),
    SIGAPP_402("SIGAPP_402", "Token expirado"),
    SIGAPP_403("SIGAPP_403", "Token invalido"),
    SIGAPP_404("SIGAPP_404", "Recurso no encontrado"),
    SIGAPP_405("SIGAPP_405", "Usuario no autorizado"),
    SIGAPP_406("SIGAPP_406", "Usuario inactivo"),
    SIGAPP_407("SIGAPP_407", "Usuario bloqueado"),
    SIGAPP_408("SIGAPP_408", "Token no proporcionado"),
    // Errores de infraestructura
    SIGAPP_500("SIGAPP_500", "Error interno del servidor");

    private final String code;
    private final String message;

}
