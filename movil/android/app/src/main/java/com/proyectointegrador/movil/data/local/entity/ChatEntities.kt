package com.proyectointegrador.movil.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * H55 y H65: copia local del chat para leerlo sin conexión. Es información privada de la sesión actual:
 * se borra al iniciar sesión con otra cuenta y al cerrar sesión.
 */
@Entity(tableName = "conversaciones_cache")
data class ConversacionCache(@PrimaryKey val id: Long, val contenido: String, val actualizadoEn: Long)

@Entity(tableName = "mensajes_chat_cache", indices = [Index("conversacionId")])
data class MensajeChatCache(@PrimaryKey val id: Long, val conversacionId: Long, val contenido: String)

/** Cola de salida: mensajes de texto escritos sin conexión, a la espera de enviarse en orden. */
@Entity(tableName = "mensajes_pendientes", indices = [Index("conversacionId")])
data class MensajePendienteEntity(
    @PrimaryKey(autoGenerate = true) val localId: Long = 0,
    val conversacionId: Long,
    val texto: String,
    val creadoEn: Long,
    /** H65: identificador único del envío; se manda como Idempotency-Key en cada reintento para que no se duplique. */
    @androidx.room.ColumnInfo(defaultValue = "''") val idCliente: String = ""
)

/**
 * H65: publicaciones escritas sin conexión (sin foto) a la espera de enviarse. Es información privada de la sesión:
 * se borra al cerrar sesión y al entrar con otra cuenta. [idCliente] viaja como Idempotency-Key en cada reintento.
 */
@Entity(tableName = "publicaciones_pendientes")
data class PublicacionPendienteEntity(
    @PrimaryKey(autoGenerate = true) val localId: Long = 0,
    val idCliente: String,
    val contenido: String,
    val creadoEn: Long
)
