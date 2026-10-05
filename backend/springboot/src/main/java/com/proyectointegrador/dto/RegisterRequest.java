package com.proyectointegrador.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * H11 - Datos de registro.
 * La contraseña debe tener al menos 8 caracteres, una mayúscula, una minúscula,
 * un número y un símbolo (máx. 72 por el límite de BCrypt).
 */
public record RegisterRequest(
        @NotBlank(message = "es obligatorio")
        @Size(max = 100, message = "no debe superar los 100 caracteres")
        @Pattern(regexp = "^[\\p{L} '-]+$", message = "solo puede contener letras")
        String nombre,

        @NotBlank(message = "es obligatorio")
        @Size(max = 100, message = "no debe superar los 100 caracteres")
        @Pattern(regexp = "^[\\p{L} '-]+$", message = "solo puede contener letras")
        String apellido,

        @NotBlank(message = "es obligatorio")
        @Email(message = "no es válido")
        @Size(max = 150, message = "no debe superar los 150 caracteres")
        String correo,

        @NotBlank(message = "es obligatorio")
        @Size(max = 72, message = "no debe superar los 72 caracteres")
        @Pattern(
                regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{8,}$",
                message = "debe tener al menos 8 caracteres, una mayúscula, una minúscula, un número y un símbolo")
        String password
) {
}
