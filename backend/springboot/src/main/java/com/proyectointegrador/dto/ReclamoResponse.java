package com.proyectointegrador.dto;

import com.proyectointegrador.entity.Reclamo;

import java.time.LocalDateTime;

public record ReclamoResponse(
        Long id,
        Long objetoId,
        String estado,
        LocalDateTime fechaReclamo,
        boolean repetido
) {
    public static ReclamoResponse from(Reclamo reclamo, boolean repetido) {
        return new ReclamoResponse(reclamo.getId(), reclamo.getObjeto().getId(), reclamo.getEstado(),
                reclamo.getFechaReclamo(), repetido);
    }
}
