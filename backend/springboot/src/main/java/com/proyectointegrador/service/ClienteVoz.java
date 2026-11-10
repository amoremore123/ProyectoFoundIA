package com.proyectointegrador.service;

// Fase 6 - Servicio externo de voz. Se usa una interfaz para probar sin llamar a ElevenLabs.
public interface ClienteVoz {

    /** true si hay clave y voz configuradas en el servidor. */
    boolean configurado();

    /** Convierte el texto en audio MP3. */
    byte[] sintetizar(String texto);
}
