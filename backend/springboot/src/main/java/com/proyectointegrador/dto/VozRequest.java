package com.proyectointegrador.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Fase 6 - Texto de una respuesta de FINDI que se convertirá en audio.
public record VozRequest(
        @NotBlank(message = "No hay texto para leer")
        @Size(max = 600, message = "El texto para la voz no puede superar los 600 caracteres")
        String texto
) {
}
