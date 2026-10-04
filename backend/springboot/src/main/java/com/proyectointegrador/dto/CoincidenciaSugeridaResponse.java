package com.proyectointegrador.dto;

import java.math.BigDecimal;
import java.time.LocalDate;

public record CoincidenciaSugeridaResponse(
        Long objetoId,
        String nombre,
        String tipo,
        String categoria,
        String ubicacion,
        LocalDate fechaObjeto,
        BigDecimal porcentaje,
        String nivel
) {
}
