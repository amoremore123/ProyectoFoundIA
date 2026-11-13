package com.proyectointegrador.dto;

import java.util.List;

/**
 * Fase 5 - Lo que la IA entendió de la frase, ya validado por el backend.
 * Es el contrato que la web ya espera (utils/asistente.js → adaptarInterpretacion):
 * la IA solo interpreta; la búsqueda la hace el sistema con datos reales.
 *
 * @param intencion   buscar, publicar, mis_reportes, como_funciona, ayuda, saludo, gracias o vacio
 * @param tipoBuscado tipo de publicación a buscar: ENCONTRADO (perdí algo), PERDIDO (encontré algo) o vacío
 * @param categoria   categoría activa del catálogo o null
 */
public record InterpretacionResponse(
        String intencion,
        String tipoBuscado,
        CategoriaResumen categoria,
        String ubicacion,
        List<String> palabras,
        List<String> colores
) {
}
