package com.proyectointegrador.dto;

import com.proyectointegrador.entity.Notificacion;

import java.time.LocalDateTime;

public record NotificacionResponse(
        Long id,
        String titulo,
        String mensaje,
        String tipo,
        Boolean leida,
        LocalDateTime fechaCreacion
) {

    public static NotificacionResponse from(Notificacion notificacion) {
        return new NotificacionResponse(
                notificacion.getId(),
                notificacion.getTitulo(),
                notificacion.getMensaje(),
                notificacion.getTipo(),
                notificacion.getLeida(),
                notificacion.getFechaCreacion()
        );
    }
}
