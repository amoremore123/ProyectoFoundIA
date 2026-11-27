package com.proyectointegrador.movil.domain

import java.util.Locale

/** Coordenadas de una publicación, en grados decimales. */
data class Coordenadas(val latitud: Double, val longitud: Double)

/** Los lugares del campus que ofrece la web en «Recorre el campus». */
val LUGARES_CAMPUS = listOf("Biblioteca", "Cafetería", "Laboratorios", "Talleres", "Aulas", "Auditorio")

fun coordenadasValidas(latitud: Double?, longitud: Double?): Boolean =
    latitud != null && longitud != null && latitud in -90.0..90.0 && longitud in -180.0..180.0 && !latitud.isNaN() && !longitud.isNaN()

/** Siete decimales, como la web. */
fun redondearCoordenada(valor: Double): Double = Math.round(valor * 1e7) / 1e7

/** Texto de ubicación para una publicación hecha con GPS: la dirección si se pudo resolver o las coordenadas. */
fun textoUbicacionGps(direccion: String?, coordenadas: Coordenadas): String =
    (direccion?.trim()?.takeIf { it.isNotBlank() }
        ?: String.format(Locale.ROOT, "Ubicación actual (%.5f, %.5f)", coordenadas.latitud, coordenadas.longitud)).take(255)

/** Enlace `geo:` para abrir el punto en la app de mapas del dispositivo. */
fun enlaceMapas(latitud: Double, longitud: Double, etiqueta: String): String {
    val nombre = java.net.URLEncoder.encode(etiqueta.take(80), "UTF-8").replace("+", "%20")
    return String.format(Locale.ROOT, "geo:%.7f,%.7f?q=%.7f,%.7f(%s)", latitud, longitud, latitud, longitud, nombre)
}
