package com.proyectointegrador.service;

// Fase 5 - Acceso al modelo de IA. Se usa una interfaz para probar el asistente sin llamar al servicio real.
public interface ClienteIa {

    /** true si hay una clave configurada en el servidor. */
    boolean configurado();

    /** Envía las instrucciones y el mensaje; devuelve el texto JSON que respondió el modelo. */
    String completarJson(String instrucciones, String mensaje);
}
