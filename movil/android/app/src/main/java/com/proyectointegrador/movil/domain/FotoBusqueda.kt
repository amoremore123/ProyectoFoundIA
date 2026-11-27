package com.proyectointegrador.movil.domain

import com.proyectointegrador.movil.model.FotoBusquedaRespuesta

/** La API acepta hasta 2 MiB ya decodificada. La web reduce la foto a 1024 px antes de enviarla. */
const val LADO_MAXIMO_FOTO_BUSQUEDA = 1024
const val BYTES_MAXIMOS_FOTO_BUSQUEDA = 2 * 1024 * 1024

/** Tamaño final de la foto manteniendo la proporción y sin agrandarla. */
fun ladoReducido(ancho: Int, alto: Int, maximo: Int = LADO_MAXIMO_FOTO_BUSQUEDA): Pair<Int, Int> {
    require(ancho > 0 && alto > 0 && maximo > 0)
    val escala = minOf(1.0, maximo.toDouble() / maxOf(ancho, alto))
    return maxOf(1, Math.round(ancho * escala).toInt()) to maxOf(1, Math.round(alto * escala).toInt())
}

/** Qué se busca según lo que reconoció la IA: las palabras clave y, si no hay, la descripción. Null si no reconoció nada. */
fun textoDeBusquedaPorFoto(respuesta: FotoBusquedaRespuesta): String? {
    val palabras = respuesta.palabras.orEmpty().map { it.trim() }.filter { it.isNotBlank() }.take(3)
    val texto = if (palabras.isNotEmpty()) palabras.joinToString(" ") else respuesta.descripcion.orEmpty().trim()
    return texto.takeIf { it.isNotBlank() }
}

/** Datos para aplicar la búsqueda por foto en la pantalla de Buscar. */
data class ResultadoFoto(val texto: String, val categoriaId: Long?, val descripcion: String)
