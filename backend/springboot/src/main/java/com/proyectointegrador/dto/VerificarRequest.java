package com.proyectointegrador.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/** H11 - Código de 6 dígitos enviado al correo. */
public record VerificarRequest(
        @NotBlank(message = "es obligatorio") @Email(message = "no es válido") String correo,
        @NotBlank(message = "es obligatorio") @Pattern(regexp = "^\\d{6}$", message = "debe tener 6 dígitos") String codigo
) {
}
