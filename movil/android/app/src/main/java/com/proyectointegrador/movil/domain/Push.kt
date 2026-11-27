package com.proyectointegrador.movil.domain

const val CANAL_AVISOS = "avisos"
const val CANAL_MENSAJES = "mensajes"

/** Destinos de la app a los que puede llevar un aviso push. */
sealed interface DestinoPush {
    data class Detalle(val objetoId: Long) : DestinoPush
    data class Conversacion(val id: Long) : DestinoPush
    data object Avisos : DestinoPush
}

/**
 * Traduce el `enlace` del aviso (la misma ruta que usa la web) a una pantalla de la app.
 * Cualquier valor desconocido o malformado abre la bandeja de avisos; nunca navega a un destino arbitrario.
 */
fun destinoDeEnlace(enlace: String?): DestinoPush {
    val ruta = enlace?.trim()?.substringBefore("?")?.substringBefore("#")?.trim('/') ?: return DestinoPush.Avisos
    val partes = ruta.split("/")
    if (partes.size == 2) {
        val id = partes[1].toLongOrNull()?.takeIf { it > 0 }
        if (id != null) {
            when (partes[0]) {
                "objeto", "objetos" -> return DestinoPush.Detalle(id)
                "conversacion", "conversaciones", "chat" -> return DestinoPush.Conversacion(id)
            }
        }
    }
    return DestinoPush.Avisos
}

fun canalDeTipo(tipo: String?): String = if (tipo == "MENSAJE_CHAT") CANAL_MENSAJES else CANAL_AVISOS
