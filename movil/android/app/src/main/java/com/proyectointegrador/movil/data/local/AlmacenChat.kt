package com.proyectointegrador.movil.data.local

import androidx.room.withTransaction
import com.google.gson.Gson
import com.proyectointegrador.movil.data.local.entity.*
import com.proyectointegrador.movil.model.Conversacion
import com.proyectointegrador.movil.model.MensajeChat

data class PendienteChat(val localId: Long, val conversacionId: Long, val texto: String, val idCliente: String = "")

/** Dónde se guarda el chat para verlo sin conexión (H55) y la cola de envío (H65). */
interface AlmacenChat {
    suspend fun conversaciones(): List<Conversacion>
    suspend fun guardarConversaciones(lista: List<Conversacion>)
    suspend fun mensajes(conversacionId: Long): List<MensajeChat>
    suspend fun guardarMensajes(conversacionId: Long, lista: List<MensajeChat>)
    suspend fun pendientes(conversacionId: Long): List<PendienteChat>
    suspend fun agregarPendiente(conversacionId: Long, texto: String, idCliente: String): PendienteChat
    suspend fun quitarPendiente(localId: Long)
    /** Se llama al cerrar sesión o al entrar con otra cuenta: el chat es privado de cada sesión. */
    suspend fun limpiar()
}

/** Límite de 200 mensajes por conversación. Room ejecuta fuera del hilo de la interfaz. */
class RoomAlmacenChat(private val db: FoundIADatabase, private val reloj: () -> Long = System::currentTimeMillis) : AlmacenChat {
    private val dao = db.chatDao()
    private val gson = Gson()

    override suspend fun conversaciones() = dao.conversaciones().mapNotNull { runCatching { gson.fromJson(it.contenido, Conversacion::class.java) }.getOrNull() }

    override suspend fun guardarConversaciones(lista: List<Conversacion>) = db.withTransaction {
        dao.borrarConversaciones()
        dao.guardarConversaciones(lista.map { ConversacionCache(it.id, gson.toJson(it), reloj()) })
    }

    override suspend fun mensajes(conversacionId: Long) = dao.mensajes(conversacionId).mapNotNull { runCatching { gson.fromJson(it.contenido, MensajeChat::class.java) }.getOrNull() }

    override suspend fun guardarMensajes(conversacionId: Long, lista: List<MensajeChat>) = db.withTransaction {
        dao.guardarMensajes(lista.map { MensajeChatCache(it.id, conversacionId, gson.toJson(it)) })
        dao.limitarMensajes(conversacionId, 200)
    }

    override suspend fun pendientes(conversacionId: Long) = dao.pendientes(conversacionId).map { PendienteChat(it.localId, it.conversacionId, it.texto, it.idCliente) }

    override suspend fun agregarPendiente(conversacionId: Long, texto: String, idCliente: String): PendienteChat {
        val id = dao.agregarPendiente(MensajePendienteEntity(conversacionId = conversacionId, texto = texto, creadoEn = reloj(), idCliente = idCliente))
        return PendienteChat(id, conversacionId, texto, idCliente)
    }

    override suspend fun quitarPendiente(localId: Long) = dao.quitarPendiente(localId)

    override suspend fun limpiar() = db.withTransaction {
        dao.borrarConversaciones(); dao.borrarMensajes(); dao.borrarPendientes()
    }
}
