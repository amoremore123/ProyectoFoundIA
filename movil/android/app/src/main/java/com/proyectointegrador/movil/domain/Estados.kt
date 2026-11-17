package com.proyectointegrador.movil.domain

import com.proyectointegrador.movil.model.Categoria
import java.text.Collator
import java.util.Locale

/** Texto en español para el estado técnico de una publicación, igual que la web. */
fun etiquetaEstadoObjeto(estado: String?): String = when (estado) {
    null, "" -> "Sin estado"
    "ACTIVO" -> "Activo"
    "EN_CUSTODIA" -> "En custodia"
    "RECUPERADO" -> "Recuperado"
    "OCULTO" -> "Oculto"
    "ELIMINADO" -> "Eliminado"
    else -> estado.lowercase().replace('_', ' ').replaceFirstChar { it.uppercase() }
}

/** Solo un objeto público y activo tiene coincidencias sugeridas. En custodia la API responde 404. */
fun admiteCoincidencias(estado: String?): Boolean = estado == null || estado == "ACTIVO"

/** Categorías en orden alfabético del español, con «Otros» siempre al final. */
fun ordenarCategorias(categorias: List<Categoria>): List<Categoria> {
    val coleccion = Collator.getInstance(Locale("es", "PE")).apply { strength = Collator.PRIMARY }
    return categorias.sortedWith(
        compareBy<Categoria> { it.nombre.orEmpty().trim().equals("Otros", ignoreCase = true) }
            .thenComparator { a, b -> coleccion.compare(a.nombre.orEmpty(), b.nombre.orEmpty()) }
    )
}
