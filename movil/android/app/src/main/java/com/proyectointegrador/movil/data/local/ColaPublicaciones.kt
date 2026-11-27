package com.proyectointegrador.movil.data.local

import com.google.gson.Gson
import com.proyectointegrador.movil.data.local.entity.PublicacionPendienteEntity
import com.proyectointegrador.movil.model.PublicacionRequest

data class PublicacionPendiente(val localId: Long, val idCliente: String, val datos: PublicacionRequest, val creadoEn: Long)

/** H65: cola de salida de publicaciones escritas sin conexión. */
interface ColaPublicaciones {
    suspend fun agregar(idCliente: String, datos: PublicacionRequest): PublicacionPendiente
    suspend fun pendientes(): List<PublicacionPendiente>
    suspend fun quitar(localId: Long)
    /** Al cerrar sesión o entrar con otra cuenta: las publicaciones en espera son de quien las escribió. */
    suspend fun limpiar()
}

class RoomColaPublicaciones(db: FoundIADatabase, private val reloj: () -> Long = System::currentTimeMillis) : ColaPublicaciones {
    private val dao = db.publicacionesPendientesDao()
    private val gson = Gson()

    override suspend fun agregar(idCliente: String, datos: PublicacionRequest): PublicacionPendiente {
        val ahora = reloj()
        val id = dao.agregar(PublicacionPendienteEntity(idCliente = idCliente, contenido = gson.toJson(datos), creadoEn = ahora))
        return PublicacionPendiente(id, idCliente, datos, ahora)
    }

    override suspend fun pendientes() = dao.pendientes().mapNotNull { fila ->
        runCatching { gson.fromJson(fila.contenido, PublicacionRequest::class.java) }.getOrNull()
            ?.let { PublicacionPendiente(fila.localId, fila.idCliente, it, fila.creadoEn) }
    }

    override suspend fun quitar(localId: Long) = dao.quitar(localId)
    override suspend fun limpiar() = dao.borrar()
}
