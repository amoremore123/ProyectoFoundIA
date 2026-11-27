package com.proyectointegrador.movil.domain

/** H68: tipos de aviso y canales, tal como los define docs/api/sprint4-h68.md. */
val TIPOS_DE_AVISO = listOf(
    "coincidencias" to "Nuevas coincidencias",
    "contactos" to "Solicitudes de contacto",
    "reclamos" to "Reclamos de objetos",
    "recordatorios" to "Recordatorios de objetos",
    "mensajes" to "Mensajes del chat",
    "sistema" to "Avisos de la biblioteca"
)

val CANALES_DE_AVISO = listOf(
    "PUSH" to "Teléfono",
    "CORREO" to "Correo",
    "AMBOS" to "Teléfono y correo",
    "NINGUNO" to "Solo en la bandeja"
)

fun canalValido(canal: String?) = CANALES_DE_AVISO.any { it.first == canal }

/** Por defecto todo llega por push, como antes de H68. */
fun canalDe(canales: Map<String, String>?, tipo: String): String = canales?.get(tipo)?.takeIf { canalValido(it) } ?: "PUSH"

fun etiquetaCanal(canal: String?) = CANALES_DE_AVISO.firstOrNull { it.first == canal }?.second ?: "Teléfono"

/** H71: a qué pantalla de la app lleva el enlace de un aviso destacado (null si no hay pantalla equivalente). */
fun rutaInternaDeEnlace(enlace: String?): String? {
    val limpio = enlace?.trim()?.substringBefore("#")?.substringBefore("?")?.trim('/') ?: return null
    if (limpio == "explorar" || limpio == "buscar") return "buscar"
    return when (val destino = destinoDeEnlace(enlace)) {
        is DestinoPush.Detalle -> "detalle/${destino.objetoId}"
        is DestinoPush.Conversacion -> "conversacion/${destino.id}"
        DestinoPush.Avisos -> null
    }
}
