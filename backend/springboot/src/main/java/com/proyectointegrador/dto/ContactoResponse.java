package com.proyectointegrador.dto;

import com.proyectointegrador.entity.Contacto;

import java.time.LocalDateTime;

public record ContactoResponse(
        Long id,
        Long coincidenciaId,
        String estado,
        LocalDateTime fechaContacto,
        boolean repetido
) {
    public static ContactoResponse from(Contacto contacto, boolean repetido) {
        return new ContactoResponse(contacto.getId(), contacto.getCoincidencia().getId(),
                contacto.getEstado().name(), contacto.getFechaContacto(), repetido);
    }
}
