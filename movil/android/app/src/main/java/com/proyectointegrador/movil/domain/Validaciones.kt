package com.proyectointegrador.movil.domain

private val PARTE_TOKEN = "[A-Za-z0-9_-]{8,}"
private val TOKEN_ETIQUETA = Regex("^$PARTE_TOKEN\\.$PARTE_TOKEN\\.$PARTE_TOKEN$")
private val TOKEN_RETIRO = Regex("^[A-Za-z0-9_-]{16,64}$")

/** La etiqueta de custodia lleva el enlace `…?t=<token firmado>` o, en las etiquetas antiguas, el token solo. */
fun esEtiquetaCustodia(valor:String):Boolean {
    val texto=valor.trim()
    if(texto.length !in 1..500) return false
    if(!texto.startsWith("http://") && !texto.startsWith("https://")) return TOKEN_ETIQUETA.matches(texto)
    val token=texto.substringAfter("?","").split("&").firstOrNull { it.startsWith("t=") }?.removePrefix("t=") ?: return false
    return TOKEN_ETIQUETA.matches(token)
}

/** El QR de retiro es un código opaco de un solo uso, sin puntos ni enlaces. */
fun esCodigoRetiro(valor:String)=TOKEN_RETIRO.matches(valor.trim())

fun correoValido(valor:String)=valor.trim().length<=150 && Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$").matches(valor.trim())
fun passwordValida(valor:String)=valor.length>=8 && valor.toByteArray(Charsets.UTF_8).size<=72 &&
    valor.any(Char::isUpperCase) && valor.any(Char::isLowerCase) && valor.any(Char::isDigit) && valor.any {!it.isLetterOrDigit()}
