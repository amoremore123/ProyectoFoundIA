package com.proyectointegrador.dto;

import com.proyectointegrador.validation.PasswordBcrypt;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;

/**
 * H35 - Contraseña nueva con el token del enlace.
 * El token es base64url (como el que acepta Android: 16 a 2048 caracteres).
 * La contraseña sigue las mismas reglas que el registro (H11).
 */
public record RestablecerPasswordRequest(
        @NotBlank(message = "es obligatorio")
        @Pattern(regexp = "^[A-Za-z0-9_-]{16,2048}$", message = "no es válido")
        String token,

        @NotBlank(message = "es obligatoria")
        @PasswordBcrypt
        @Pattern(
                regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{8,}$",
                message = "debe tener al menos 8 caracteres, una mayúscula, una minúscula, un número y un símbolo")
        String password
) {
}
