package com.proyectointegrador.dto;

import com.proyectointegrador.validation.PasswordBcrypt;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * H13 - Datos que el usuario puede cambiar de su propio perfil.
 * Solo nombre, apellido y, opcionalmente, la contraseña: rol, estado, correo e
 * identidad no se aceptan aquí (cualquier otro campo del JSON se ignora).
 * Nombre y apellido siguen las mismas reglas que el registro (H11).
 * Una contraseña nueva debe cumplir las reglas del registro y venir con la actual.
 */
public record PerfilUpdateRequest(
        @NotBlank(message = "es obligatorio")
        @Size(max = 100, message = "no debe superar los 100 caracteres")
        @Pattern(regexp = "^\\s*[\\p{L} '-]+\\s*$", message = "solo puede contener letras")
        String nombre,

        @NotBlank(message = "es obligatorio")
        @Size(max = 100, message = "no debe superar los 100 caracteres")
        @Pattern(regexp = "^\\s*[\\p{L} '-]+\\s*$", message = "solo puede contener letras")
        String apellido,

        @PasswordBcrypt
        @Pattern(
                regexp = "^$|^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{8,}$",
                message = "debe tener al menos 8 caracteres, una mayúscula, una minúscula, un número y un símbolo")
        String password,

        String passwordActual
) {

    /** true si el usuario pidió cambiar la contraseña. */
    public boolean cambiaPassword() {
        return password != null && !password.isEmpty();
    }
}
