package com.proyectointegrador.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** H57: token de Firebase Cloud Messaging del teléfono. */
public record DispositivoRequest(
        @NotBlank(message = "es obligatorio")
        @Size(min = 20, max = 512, message = "debe tener entre 20 y 512 caracteres")
        @Pattern(regexp = "[A-Za-z0-9:_-]+", message = "tiene caracteres no válidos")
        String token,
        @NotBlank(message = "es obligatorio")
        @Pattern(regexp = "ANDROID", message = "solo se admite ANDROID")
        String plataforma
) {
}
