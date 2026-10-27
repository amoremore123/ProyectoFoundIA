package com.proyectointegrador.movil.data.local.dao

import androidx.room.*
import com.proyectointegrador.movil.data.local.entity.*
import kotlinx.coroutines.flow.Flow

@Dao
interface CacheDao {
    @Query("SELECT * FROM publicaciones_cache WHERE id = :id") suspend fun publicacion(id: Long): PublicacionCache?
    @Query("SELECT * FROM publicaciones_cache WHERE id IN (:ids)") suspend fun publicaciones(ids: List<Long>): List<PublicacionCache>
    @Query("SELECT * FROM consultas_cache WHERE clave = :clave") suspend fun consulta(clave: String): ConsultaCache?
    @Upsert suspend fun guardarPublicaciones(filas: List<PublicacionCache>)
    @Upsert suspend fun guardarConsulta(fila: ConsultaCache)
    @Query("DELETE FROM publicaciones_cache WHERE id = :id") suspend fun eliminarPublicacion(id: Long)
    @Query("DELETE FROM publicaciones_cache") suspend fun borrarPublicaciones()
    @Query("DELETE FROM consultas_cache") suspend fun borrarConsultas()
    @Query("DELETE FROM publicaciones_cache WHERE id NOT IN (SELECT id FROM publicaciones_cache ORDER BY actualizadoEn DESC, id DESC LIMIT 200)") suspend fun limitarPublicaciones()
    @Query("DELETE FROM consultas_cache WHERE clave NOT IN (SELECT clave FROM consultas_cache ORDER BY actualizadoEn DESC, clave LIMIT 20)") suspend fun limitarConsultas()
    @Query("SELECT * FROM categorias_cache ORDER BY orden, id") fun observarCategorias(): Flow<List<CategoriaCache>>
    @Query("SELECT * FROM categorias_cache ORDER BY orden, id") suspend fun categorias(): List<CategoriaCache>
    @Upsert suspend fun guardarCategorias(filas: List<CategoriaCache>)
    @Query("DELETE FROM categorias_cache") suspend fun borrarCategorias()
}
