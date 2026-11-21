package com.proyectointegrador.movil.model

/** Contrato de docs/api/sprint4-h55.md. */
data class Participante(val id: Long?, val nombre: String)

data class MensajeChat(
    val id: Long, val autorId: Long, val autorNombre: String?, val texto: String?,
    val imagenUrl: String?, val enviadoEn: String?
)

data class Conversacion(
    val id: Long, val tipo: String, val objetoId: Long, val objetoNombre: String?, val estado: String,
    val soloLectura: Boolean, val bloqueada: Boolean, val otro: Participante?,
    val ultimoMensaje: MensajeChat?, val sinLeer: Long
)

data class MensajesRespuesta(val mensajes: List<MensajeChat>, val ultimoId: Long, val soloLectura: Boolean, val bloqueada: Boolean)

data class IniciarConversacion(val contactoId: Long? = null, val reclamoId: Long? = null)
data class EnviarMensaje(val texto: String)
data class ReportarMensaje(val motivo: String)
