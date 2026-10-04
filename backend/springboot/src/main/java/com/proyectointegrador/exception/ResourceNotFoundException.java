package com.proyectointegrador.exception;

public class ResourceNotFoundException extends RuntimeException {

    public ResourceNotFoundException(String mensaje) {
        super(mensaje);
    }

    public ResourceNotFoundException(String recurso, String detalle) {
        super(recurso + " " + detalle);
    }
}
