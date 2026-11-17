package com.proyectointegrador.movil.domain

import java.text.SimpleDateFormat
import java.util.Locale

/** Filtros de la búsqueda avanzada. Son los que ya admite `GET /api/objetos/buscar` y que usa la web. */
data class FiltrosAvanzados(
    val ubicacion: String = "",
    val desde: String = "",
    val hasta: String = "",
    val latitud: Double? = null,
    val longitud: Double? = null,
    val radioKm: Double? = null,
) {
    val activos: Boolean get() = ubicacion.isNotBlank() || desde.isNotBlank() || hasta.isNotBlank() || latitud != null
}

val RADIOS_KM = listOf(0.5, 1.0, 2.0, 5.0)
const val RADIO_KM_POR_DEFECTO = 2.0

/** Fecha `aaaa-mm-dd` real, sin corregir días imposibles como el 30 de febrero. */
fun fechaIsoValida(texto: String): Boolean = Regex("[0-9]{4}-[0-9]{2}-[0-9]{2}").matches(texto) &&
    runCatching { SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply { isLenient = false }.parse(texto) != null }.getOrDefault(false)

/** Mensaje en español si los filtros no son válidos, o null. Las fechas ISO se comparan como texto. */
fun errorDeFiltros(f: FiltrosAvanzados): String? = when {
    f.ubicacion.length > 255 -> "El lugar admite hasta 255 caracteres."
    f.desde.isNotBlank() && !fechaIsoValida(f.desde) -> "La fecha «desde» no es válida."
    f.hasta.isNotBlank() && !fechaIsoValida(f.hasta) -> "La fecha «hasta» no es válida."
    f.desde.isNotBlank() && f.hasta.isNotBlank() && f.desde > f.hasta -> "«Desde» no puede ser posterior a «hasta»."
    (f.latitud == null) != (f.longitud == null) -> "La ubicación de cercanía está incompleta."
    f.latitud != null && !coordenadasValidas(f.latitud, f.longitud) -> "Las coordenadas de cercanía no son válidas."
    f.radioKm != null && f.radioKm !in 0.1..50.0 -> "El radio debe estar entre 0.1 y 50 km."
    else -> null
}

/** Clave y texto de cada filtro activo, para los chips con «×». La clave dice qué se quita al tocarlo. */
fun etiquetasDeFiltros(f: FiltrosAvanzados): List<Pair<String, String>> = buildList {
    if (f.ubicacion.isNotBlank()) add("ubicacion" to "Lugar: ${f.ubicacion.trim()}")
    if (f.desde.isNotBlank() || f.hasta.isNotBlank()) add("fechas" to when {
        f.desde.isNotBlank() && f.hasta.isNotBlank() -> "Del ${f.desde} al ${f.hasta}"
        f.desde.isNotBlank() -> "Desde ${f.desde}"
        else -> "Hasta ${f.hasta}"
    })
    if (f.latitud != null) add("cerca" to "Cerca de mí (${formatoRadio(f.radioKm ?: RADIO_KM_POR_DEFECTO)})")
}

fun formatoRadio(km: Double): String = if (km < 1) "${(km * 1000).toInt()} m" else "${if (km % 1.0 == 0.0) km.toInt() else km} km"

/** Quita el filtro de esa clave y conserva los demás. */
fun sinFiltro(f: FiltrosAvanzados, clave: String): FiltrosAvanzados = when (clave) {
    "ubicacion" -> f.copy(ubicacion = "")
    "fechas" -> f.copy(desde = "", hasta = "")
    "cerca" -> f.copy(latitud = null, longitud = null, radioKm = null)
    else -> f
}
