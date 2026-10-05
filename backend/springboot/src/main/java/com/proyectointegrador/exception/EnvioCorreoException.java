package com.proyectointegrador.exception;

/** H11 - No se pudo enviar el correo (HTTP 503). */
public class EnvioCorreoException extends RuntimeException {

    public EnvioCorreoException(String mensaje) {
        super(mensaje);
    }
}
