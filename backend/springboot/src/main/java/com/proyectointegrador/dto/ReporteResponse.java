package com.proyectointegrador.dto;

import java.time.Instant;

public record ReporteResponse(Long id, Long objetoId, String motivo, String estado,
                              Instant fechaReporte, boolean repetido) {
}
