package com.proyectointegrador.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record ReporteRequest(
        @NotBlank @Pattern(regexp = "CONTENIDO_INAPROPIADO|INFORMACION_FALSA|SPAM|OTRO",
                message = "debe ser un motivo admitido") String motivo,
        @Size(max = 2000, message = "no puede superar 2000 caracteres") String descripcion) {
}
