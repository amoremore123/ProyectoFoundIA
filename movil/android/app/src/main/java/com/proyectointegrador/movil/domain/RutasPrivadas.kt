package com.proyectointegrador.movil.domain

private val FOTO_CHAT = Regex("^/api/conversaciones/[0-9]{1,18}/mensajes/[0-9]{1,18}/foto$")
private val FOTO_OTRA_PERSONA = Regex("^/api/conversaciones/[0-9]{1,18}/foto$")

/**
 * H62 y H64: las fotos del chat y la foto de perfil de la otra persona son privadas: solo se piden con sesión,
 * y solo a estas rutas de nuestra propia API. Cualquier otra ruta (por ejemplo una dirección externa) se rechaza
 * para no enviar el token de la sesión a otro sitio.
 */
fun esRutaFotoPrivada(ruta: String?): Boolean = ruta != null && (FOTO_CHAT.matches(ruta) || FOTO_OTRA_PERSONA.matches(ruta))

fun rutaFotoDeConversacion(conversacionId: Long) = "/api/conversaciones/$conversacionId/foto"
