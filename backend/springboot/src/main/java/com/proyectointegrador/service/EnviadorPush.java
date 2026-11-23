package com.proyectointegrador.service;

/** H57: envía un push a un teléfono. La implementación real es {@link ClienteFcm}. */
public interface EnviadorPush {

    enum Resultado { ENVIADO, TOKEN_INVALIDO, ERROR }

    record Mensaje(String titulo, String cuerpo, Long notificacionId, String tipo, String enlace) {}

    /** {@code false} si no hay cuenta de servicio de Firebase: el push queda apagado. */
    boolean disponible();

    Resultado enviar(String tokenDispositivo, Mensaje mensaje);
}
