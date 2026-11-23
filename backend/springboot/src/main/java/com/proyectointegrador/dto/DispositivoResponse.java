package com.proyectointegrador.dto;

import com.proyectointegrador.entity.Dispositivo;

import java.time.LocalDateTime;

/** H57: nunca incluye el token de Firebase. */
public record DispositivoResponse(Long id, String plataforma, LocalDateTime fechaRegistro, LocalDateTime fechaUltimoUso) {

    public static DispositivoResponse from(Dispositivo d) {
        return new DispositivoResponse(d.getId(), d.getPlataforma(), d.getFechaRegistro(), d.getFechaUltimoUso());
    }
}
