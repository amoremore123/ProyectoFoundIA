package com.proyectointegrador.dto;

import java.util.List;

/**
 * Lo que la IA reconoció en la foto, ya validado por el servidor. No se guarda nada:
 * la web usa estos datos para buscar entre las publicaciones reales.
 *
 * @param descripcion frase corta del objeto ("gafas de montura marrón") o vacío si no se reconoció
 * @param categoria   categoría activa del catálogo o null
 * @param palabras    hasta 6 términos útiles para buscar, en minúsculas
 * @param colores     colores del objeto, en minúsculas
 */
public record FotoBusquedaResponse(
        String descripcion,
        CategoriaResumen categoria,
        List<String> palabras,
        List<String> colores
) {
}
