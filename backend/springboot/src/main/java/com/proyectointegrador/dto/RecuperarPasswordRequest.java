package com.proyectointegrador.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** H35 - Solicitud del enlace de recuperación. */
public record RecuperarPasswordRequest(
        @NotBlank(message = "es obligatorio")
        @Email(message = "no es válido")
        @Size(max = 150, message = "no debe superar los 150 caracteres")
        String correo
) {
}
