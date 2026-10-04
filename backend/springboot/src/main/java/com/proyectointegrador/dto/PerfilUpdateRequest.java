package com.proyectointegrador.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record PerfilUpdateRequest(
        @NotBlank(message = "es obligatorio") String nombre,
        @NotBlank(message = "es obligatorio") String apellido,
        @Size(min = 6, message = "debe tener al menos 6 caracteres") String password
) {
}
