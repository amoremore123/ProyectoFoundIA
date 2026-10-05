package com.proyectointegrador.exception;

/** H11 - Código de verificación incorrecto o expirado (HTTP 400). */
public class CodigoVerificacionException extends RuntimeException {

    public CodigoVerificacionException(String mensaje) {
        super(mensaje);
    }
}
