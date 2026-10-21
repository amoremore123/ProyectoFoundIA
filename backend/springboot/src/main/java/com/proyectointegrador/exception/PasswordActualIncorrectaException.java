package com.proyectointegrador.exception;

/**
 * H13 - Para cambiar la contraseña se exige la actual y no coincide (HTTP 400).
 * No se usa 401 para no cerrar la sesión del usuario en la web.
 */
public class PasswordActualIncorrectaException extends RuntimeException {

    public PasswordActualIncorrectaException(String mensaje) {
        super(mensaje);
    }
}
