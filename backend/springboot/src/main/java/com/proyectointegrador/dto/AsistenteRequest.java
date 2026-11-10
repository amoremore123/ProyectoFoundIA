package com.proyectointegrador.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AsistenteRequest(
        @NotBlank(message = "no puede estar vacío")
        @Size(max = 300, message = "no puede superar 300 caracteres")
        String mensaje
) {
}
