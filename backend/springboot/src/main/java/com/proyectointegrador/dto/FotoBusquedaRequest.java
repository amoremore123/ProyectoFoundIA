package com.proyectointegrador.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Búsqueda por foto desde el chat de FINDI: imagen JPEG o PNG como data URL
 * (data:image/jpeg;base64,...). La web la reduce antes de enviarla (máx. 1024 px).
 */
public record FotoBusquedaRequest(
        @NotBlank(message = "Adjunta una foto del objeto.")
        @Size(max = 3_000_000, message = "La foto es demasiado grande.")
        String imagen
) {
}
