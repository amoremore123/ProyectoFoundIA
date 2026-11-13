package com.proyectointegrador.dto;

/**
 * H49: sugerencia de categoría y descripción. Solo sugiere: el servidor no guarda nada y la
 * persona decide si la acepta, la edita o la descarta.
 *
 * @param categoria   categoría activa del catálogo o null si no hay una clara
 * @param descripcion descripción mejorada o null si no se pudo mejorar
 * @param origen      "IA" si respondió el modelo y pasó la validación; "LOCAL" si se usó el motor propio
 */
public record SugerenciaPublicacionResponse(
        CategoriaResumen categoria,
        String descripcion,
        String origen,
        String aviso
) {
}
