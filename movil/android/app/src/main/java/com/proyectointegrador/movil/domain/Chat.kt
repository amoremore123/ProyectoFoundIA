package com.proyectointegrador.movil.domain

import com.proyectointegrador.movil.model.MensajeChat

const val MAXIMO_MENSAJE_CHAT = 1000

/** Devuelve el motivo por el que no se puede enviar el texto, o null si es válido. */
fun errorMensajeChat(texto: String): String? = when {
    texto.isBlank() -> "Escribe un mensaje."
    texto.trim().length > MAXIMO_MENSAJE_CHAT -> "El mensaje no puede pasar de $MAXIMO_MENSAJE_CHAT caracteres."
    else -> null
}

/** Junta lo ya mostrado con lo recién recibido sin duplicar ni desordenar (el sondeo y el envío pueden traer el mismo mensaje). */
fun fusionarMensajes(actuales: List<MensajeChat>, nuevos: List<MensajeChat>): List<MensajeChat> =
    (actuales + nuevos).associateBy { it.id }.values.sortedBy { it.id }

fun textoSinLeer(cantidad: Long): String? = when {
    cantidad <= 0 -> null
    cantidad > 99 -> "99+"
    else -> cantidad.toString()
}
