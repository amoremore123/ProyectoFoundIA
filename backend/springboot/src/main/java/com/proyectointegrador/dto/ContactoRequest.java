package com.proyectointegrador.dto;

import jakarta.validation.constraints.NotNull;

public record ContactoRequest(
        @NotNull(message = "es obligatorio") Long objetoPerdidoId,
        @NotNull(message = "es obligatorio") Long objetoEncontradoId
) {
}
