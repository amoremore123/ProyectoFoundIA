package com.proyectointegrador.service;

import java.util.List;

/**
 * H45: interpretación ya validada, común al motor local y al proveedor de IA.
 */
public record InterpretacionAsistente(
        String intencion,
        String tipoBuscado,
        Long categoriaId,
        String categoriaNombre,
        String ubicacion,
        List<String> palabras,
        List<String> colores
) {
}
