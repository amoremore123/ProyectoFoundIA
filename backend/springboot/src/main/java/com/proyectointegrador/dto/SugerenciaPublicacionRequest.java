package com.proyectointegrador.dto;

import jakarta.validation.constraints.Size;

/** H49: lo que la persona escribió en el paso «Información» de Publicar. No se guarda. */
public record SugerenciaPublicacionRequest(
        @Size(max = 150, message = "no debe superar los 150 caracteres") String nombre,
        @Size(max = 1000, message = "no debe superar los 1000 caracteres") String descripcion,
        String tipo
) {
}
