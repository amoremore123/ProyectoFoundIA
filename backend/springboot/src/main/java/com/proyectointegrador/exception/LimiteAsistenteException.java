package com.proyectointegrador.exception;

/** H45 - Se superó el límite de consultas al asistente (HTTP 429). */
public class LimiteAsistenteException extends RuntimeException {

    private final long segundosRestantes;

    public LimiteAsistenteException(String mensaje, long segundosRestantes) {
        super(mensaje);
        this.segundosRestantes = segundosRestantes;
    }

    public long getSegundosRestantes() {
        return segundosRestantes;
    }
}
