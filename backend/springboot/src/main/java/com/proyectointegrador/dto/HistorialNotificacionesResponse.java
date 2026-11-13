package com.proyectointegrador.dto;

import java.util.List;

/**
 * H25: una página del historial de avisos de la cuenta propia.
 * "noLeidas" cuenta todos los avisos sin leer de la cuenta, no solo los de la página.
 */
public record HistorialNotificacionesResponse(
        List<NotificacionResponse> contenido,
        int pagina,
        int tamano,
        long total,
        int totalPaginas,
        String estado,
        long noLeidas
) {
}
