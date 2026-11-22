package com.proyectointegrador.exception;

/** H72: el servidor no tiene la cuenta de servicio de Firebase; la app sigue con su sesión normal (503). */
public class FirebaseNoDisponibleException extends RuntimeException {
    public FirebaseNoDisponibleException() {
        super("Firebase no está configurado en el servidor. Puedes seguir usando FoundIA con tu sesión normal.");
    }
}
