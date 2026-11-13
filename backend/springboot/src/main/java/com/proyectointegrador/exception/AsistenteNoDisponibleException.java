package com.proyectointegrador.exception;

// Fase 5 - La IA no está configurada, no respondió a tiempo o devolvió algo inválido.
// Se responde 503 y la web usa su motor local de respaldo.
public class AsistenteNoDisponibleException extends RuntimeException {

    public AsistenteNoDisponibleException(String mensaje) {
        super(mensaje);
    }
}
