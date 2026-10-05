package com.proyectointegrador.exception;

/** H12 - La cuenta está bloqueada temporalmente por intentos fallidos (HTTP 423). */
public class CuentaBloqueadaException extends RuntimeException {

    public CuentaBloqueadaException(String mensaje) {
        super(mensaje);
    }
}
