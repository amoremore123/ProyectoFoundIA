package com.proyectointegrador.exception;

public class InvalidCredentialsException extends RuntimeException {

    public InvalidCredentialsException(String mensaje) {
        super(mensaje);
    }
}
