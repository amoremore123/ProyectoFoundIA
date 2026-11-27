package com.proyectointegrador.movil.domain

/**
 * Mensaje para la persona según el error que devuelve el reconocedor de voz del teléfono
 * (códigos de `SpeechRecognizer`). Así sabe si debe hablar más fuerte, revisar la conexión o escribir.
 */
fun mensajeErrorDictado(codigo: Int): String = when (codigo) {
    1, 2, 4 -> "El dictado necesita conexión a internet y no pudo conectarse. Revisa tu conexión o escribe tu mensaje."
    3 -> "No se pudo usar el micrófono. Revisa que otra app no lo esté usando o escribe tu mensaje."
    6 -> "No te escuché. Acércate al micrófono y vuelve a intentarlo, o escribe tu mensaje."
    7 -> "No entendí lo que dijiste. Habla más despacio en un lugar tranquilo, o escribe tu mensaje."
    8 -> "El dictado está ocupado. Espera un momento y vuelve a intentarlo."
    9 -> "Falta el permiso del micrófono. Puedes concederlo en los ajustes del teléfono o escribir tu mensaje."
    else -> "No pude reconocer la voz. Prueba en un lugar tranquilo o escribe tu mensaje."
}

/** El servidor acepta hasta 600 caracteres por lectura. Se recorta en un límite de frase cuando se puede. */
const val MAXIMO_VOZ = 600

fun textoParaVoz(texto: String): String {
    val limpio = texto.trim().replace(Regex("\\s+"), " ")
    if (limpio.length <= MAXIMO_VOZ) return limpio
    val corte = limpio.take(MAXIMO_VOZ)
    val finDeFrase = maxOf(corte.lastIndexOf(". "), corte.lastIndexOf("? "), corte.lastIndexOf("! "))
    return if (finDeFrase > MAXIMO_VOZ / 2) corte.substring(0, finDeFrase + 1) else corte
}
