package com.proyectointegrador.movil.domain

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Reglas de una publicación, iguales a las de la API y a las de la web. La usan Publicar y Editar.
 * Devuelve el mensaje de cada campo inválido; vacío si todo está bien.
 * [categoriaDisponible] dice si la categoría elegida existe y está activa en el catálogo cargado.
 */
fun validarPublicacion(
    nombre: String, descripcion: String, categoriaDisponible: Boolean, ubicacion: String,
    latitud: Double?, longitud: Double?, fecha: String, tipo: String, hoy: Date = Date(),
): Map<String, String> {
    val errores = mutableMapOf<String, String>()
    if (nombre.isBlank() || nombre.length > 150) errores["nombre"] = "Ingresa un nombre de hasta 150 caracteres."
    if (descripcion.isBlank() || descripcion.length > 16000) errores["descripcion"] = "Ingresa una descripción válida."
    if (!categoriaDisponible) errores["categoria"] = "Selecciona una categoría disponible."
    if (ubicacion.isBlank() || ubicacion.length > 255) errores["ubicacion"] = "Ingresa una ubicación de hasta 255 caracteres."
    if ((latitud == null) != (longitud == null) || (latitud != null && !coordenadasValidas(latitud, longitud))) {
        errores["ubicacion"] = "Las coordenadas de la ubicación no son válidas."
    }
    val formato = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply { isLenient = false }
    val fechaObjeto = runCatching { if (!Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}").matches(fecha)) null else formato.parse(fecha) }.getOrNull()
    val hoySinHora = formato.parse(formato.format(hoy))!!
    if (fechaObjeto == null || fechaObjeto.after(hoySinHora)) errores["fecha"] = "Ingresa una fecha válida que no sea futura."
    if (tipo !in listOf("PERDIDO", "ENCONTRADO")) errores["tipo"] = "Selecciona el tipo de objeto."
    return errores
}

/** Mismas reglas que la web y la API (H5): se edita si no está eliminada ni en custodia, y se elimina si está visible. */
fun puedeEditarPublicacion(estado: String?): Boolean = estado in listOf("ACTIVO", "RECUPERADO", "OCULTO")

fun puedeEliminarPublicacion(estado: String?): Boolean = estado == "ACTIVO" || estado == "RECUPERADO"
