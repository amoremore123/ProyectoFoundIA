package com.proyectointegrador.exception;

/** H55/H56/H61: la conversación no admite mensajes (solo lectura o bloqueada). Responde 409. */
public class ChatNoDisponibleException extends RuntimeException {
    public ChatNoDisponibleException(String mensaje) {
        super(mensaje);
    }
}
