package com.proyectointegrador.exception;

/** H11 - La cuenta aún no confirmó su correo (HTTP 403). */
public class CuentaNoVerificadaException extends RuntimeException {

    public CuentaNoVerificadaException(String mensaje) {
        super(mensaje);
    }
}
