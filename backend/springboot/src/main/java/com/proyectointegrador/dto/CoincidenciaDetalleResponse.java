package com.proyectointegrador.dto;

import java.math.BigDecimal;
import java.util.Map;

public record CoincidenciaDetalleResponse(String id, ObjetoResponse perdido, ObjetoResponse encontrado,
        BigDecimal porcentaje, String nivel, Map<String, Integer> criterios) {
}
