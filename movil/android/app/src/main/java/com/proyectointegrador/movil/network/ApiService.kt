package com.proyectointegrador.movil.network

import com.proyectointegrador.movil.model.Categoria
import com.proyectointegrador.movil.model.CoincidenciaSugerida
import com.proyectointegrador.movil.model.Objeto
import retrofit2.http.GET
import retrofit2.http.Path
import retrofit2.http.Query

/** Contrato de la API publica de Spring Boot (seccion 3 de docs/api/endpoints.md). */
interface ApiService {

    @GET("api/objetos")
    suspend fun listarObjetos(
        @Query("tipo") tipo: String? = null,
        @Query("estado") estado: String? = null,
        @Query("categoriaId") categoriaId: Long? = null
    ): List<Objeto>

    @GET("api/objetos/{id}")
    suspend fun objetoPorId(@Path("id") id: Long): Objeto

    @GET("api/objetos/buscar")
    suspend fun buscarObjetos(
        @Query("q") q: String? = null,
        @Query("tipo") tipo: String? = null,
        @Query("categoriaId") categoriaId: Long? = null
    ): List<Objeto>

    @GET("api/objetos/categoria/{id}")
    suspend fun objetosPorCategoria(@Path("id") id: Long): List<Objeto>

    @GET("api/categorias")
    suspend fun categorias(): List<Categoria>

    @GET("api/objetos/{id}/coincidencias")
    suspend fun coincidencias(@Path("id") id: Long): List<CoincidenciaSugerida>
}
