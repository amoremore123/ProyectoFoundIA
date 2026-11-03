package com.proyectointegrador.movil.data.local

import androidx.room.withTransaction
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.proyectointegrador.movil.data.local.entity.*
import com.proyectointegrador.movil.domain.Lectura
import com.proyectointegrador.movil.model.*
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface CachePublica {
    suspend fun lista(clave: String): Lectura<List<Objeto>>?
    suspend fun guardarLista(clave: String, datos: List<Objeto>)
    suspend fun detalle(id: Long): Lectura<Objeto>?
    suspend fun guardarDetalle(dato: Objeto)
    suspend fun categorias(): Lectura<List<Categoria>>?
    suspend fun guardarCategorias(datos: List<Categoria>)
    fun observarCategorias(): Flow<List<Categoria>>
    suspend fun invalidarPublicaciones()
    suspend fun retirar(id: Long)
}

/** Límite: 200 objetos, 20 consultas exactas y 24 horas. Room ejecuta fuera del hilo UI. */
class RoomCachePublica(private val db: FoundIADatabase, private val reloj: () -> Long = System::currentTimeMillis) : CachePublica {
    private val dao=db.cacheDao()
    private val gson=Gson()
    private val duracion=24*60*60*1000L
    private fun vigente(fecha: Long)=reloj()-fecha in 0..duracion
    override suspend fun lista(clave: String): Lectura<List<Objeto>>? {
        val fila=dao.consulta(clave)?.takeIf {vigente(it.actualizadoEn)} ?: return null
        val ids:List<Long> = gson.fromJson(fila.idsJson,object:TypeToken<List<Long>>(){}.type)
        val filas=dao.publicaciones(ids).associateBy {it.id}
        if(ids.any {filas[it]==null || !vigente(filas.getValue(it).actualizadoEn) || filas.getValue(it).venceEn<reloj()}) return null
        return Lectura(ids.map {gson.fromJson(filas.getValue(it).contenido,Objeto::class.java)},true,fila.actualizadoEn)
    }
    override suspend fun guardarLista(clave: String, datos: List<Objeto>) = db.withTransaction {
        // No presentar una lista truncada como si fuera la respuesta completa de una búsqueda.
        if(datos.size>200) {dao.borrarConsultas();return@withTransaction}
        val ahora=reloj()
        dao.guardarPublicaciones(datos.distinctBy {it.id}.map {PublicacionCache(it.id,gson.toJson(it),ahora,ahora+duracion)})
        dao.guardarConsulta(ConsultaCache(clave,gson.toJson(datos.map {it.id}.distinct()),ahora))
        dao.limitarPublicaciones();dao.limitarConsultas()
    }
    override suspend fun detalle(id: Long): Lectura<Objeto>? = dao.publicacion(id)?.takeIf {vigente(it.actualizadoEn) && it.venceEn>=reloj()}?.let {
        Lectura(gson.fromJson(it.contenido,Objeto::class.java),true,it.actualizadoEn)
    }
    override suspend fun guardarDetalle(dato: Objeto) = db.withTransaction {
        dao.guardarPublicaciones(listOf(PublicacionCache(dato.id,gson.toJson(dato),reloj(),reloj()+duracion)))
        dao.limitarPublicaciones()
    }
    override suspend fun categorias(): Lectura<List<Categoria>>? {
        val meta=dao.consulta("categorias")?.takeIf {vigente(it.actualizadoEn)} ?: return null
        return Lectura(dao.categorias().map {Categoria(it.id,it.nombre,it.descripcion,it.estado)},true,meta.actualizadoEn)
    }
    override suspend fun guardarCategorias(datos: List<Categoria>) = db.withTransaction {
        dao.borrarCategorias()
        dao.guardarCategorias(datos.distinctBy {it.id}.mapIndexed {i,it->CategoriaCache(it.id,it.nombre,it.descripcion,it.estado,i)})
        dao.guardarConsulta(ConsultaCache("categorias","[]",reloj()))
    }
    override fun observarCategorias() = dao.observarCategorias().map {filas->filas.map {Categoria(it.id,it.nombre,it.descripcion,it.estado)}}
    override suspend fun invalidarPublicaciones() = db.withTransaction {dao.borrarConsultas();dao.borrarPublicaciones()}
    override suspend fun retirar(id: Long) = db.withTransaction {dao.eliminarPublicacion(id);dao.borrarConsultas()}
}
