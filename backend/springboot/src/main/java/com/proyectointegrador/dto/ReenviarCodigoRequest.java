package com.proyectointegrador.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

public record ReenviarCodigoRequest(
        @NotBlank(message = "es obligatorio") @Email(message = "no es válido") String correo
) {
}
