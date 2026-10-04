package com.proyectointegrador.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record RegisterRequest(
        @NotBlank(message = "es obligatorio") String nombre,
        @NotBlank(message = "es obligatorio") String apellido,
        @NotBlank(message = "es obligatorio") @Email(message = "no es válido") String correo,
        @NotBlank(message = "es obligatorio") @Size(min = 6, message = "debe tener al menos 6 caracteres") String password
) {
}
