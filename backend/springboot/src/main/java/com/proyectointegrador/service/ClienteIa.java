package com.proyectointegrador.service;

// Fase 5 - Acceso al modelo de IA. Se usa una interfaz para probar el asistente sin llamar al servicio real.
public interface ClienteIa {

    /** true si hay una clave configurada en el servidor. */
    boolean configurado();

    /** Envía las instrucciones y el mensaje; devuelve el texto JSON que respondió el modelo. */
    String completarJson(String instrucciones, String mensaje);

    /**
     * Búsqueda por foto: envía instrucciones, un texto y una imagen (data URL JPEG/PNG) a un modelo
     * que acepta imágenes; devuelve el texto JSON de la respuesta. Por defecto no está disponible.
     */
    default String completarJsonConImagen(String instrucciones, String texto, String imagenDataUrl) {
        throw new UnsupportedOperationException("Este proveedor no acepta imágenes");
    }
}
