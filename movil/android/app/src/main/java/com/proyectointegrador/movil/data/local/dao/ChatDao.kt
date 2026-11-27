package com.proyectointegrador.movil.data.local.dao

import androidx.room.*
import com.proyectointegrador.movil.data.local.entity.*

@Dao
interface ChatDao {
    @Query("SELECT * FROM conversaciones_cache ORDER BY id DESC") suspend fun conversaciones(): List<ConversacionCache>
    @Upsert suspend fun guardarConversaciones(filas: List<ConversacionCache>)
    @Query("DELETE FROM conversaciones_cache") suspend fun borrarConversaciones()

    @Query("SELECT * FROM mensajes_chat_cache WHERE conversacionId = :conversacionId ORDER BY id") suspend fun mensajes(conversacionId: Long): List<MensajeChatCache>
    @Upsert suspend fun guardarMensajes(filas: List<MensajeChatCache>)
    @Query("DELETE FROM mensajes_chat_cache WHERE conversacionId = :conversacionId AND id NOT IN (SELECT id FROM mensajes_chat_cache WHERE conversacionId = :conversacionId ORDER BY id DESC LIMIT :limite)")
    suspend fun limitarMensajes(conversacionId: Long, limite: Int)
    @Query("DELETE FROM mensajes_chat_cache") suspend fun borrarMensajes()

    @Query("SELECT * FROM mensajes_pendientes WHERE conversacionId = :conversacionId ORDER BY localId") suspend fun pendientes(conversacionId: Long): List<MensajePendienteEntity>
    @Insert suspend fun agregarPendiente(fila: MensajePendienteEntity): Long
    @Query("DELETE FROM mensajes_pendientes WHERE localId = :localId") suspend fun quitarPendiente(localId: Long)
    @Query("DELETE FROM mensajes_pendientes") suspend fun borrarPendientes()
}
