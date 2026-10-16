package com.proyectointegrador.exception;

/** H11 - Se pidió un nuevo código antes de que termine la espera entre envíos (HTTP 429). */
public class LimiteReenvioException extends RuntimeException {

    private final long segundosRestantes;

    public LimiteReenvioException(String mensaje, long segundosRestantes) {
        super(mensaje);
        this.segundosRestantes = segundosRestantes;
    }

    public long getSegundosRestantes() {
        return segundosRestantes;
    }
}
