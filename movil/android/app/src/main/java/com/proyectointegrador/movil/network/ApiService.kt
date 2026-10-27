package com.proyectointegrador.movil.network

import com.proyectointegrador.movil.model.Categoria
import com.proyectointegrador.movil.model.CoincidenciaSugerida
import com.proyectointegrador.movil.model.Objeto
import com.proyectointegrador.movil.model.*
import retrofit2.http.DELETE
import retrofit2.http.Body
import retrofit2.http.POST
import retrofit2.http.PUT
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

    @POST("api/objetos") suspend fun publicar(@Body datos:PublicacionRequest):Objeto
    @PUT("api/objetos/{id}") suspend fun editar(@Path("id") id:Long,@Body datos:PublicacionRequest):Objeto
    @DELETE("api/objetos/{id}") suspend fun eliminar(@Path("id") id:Long):retrofit2.Response<Unit>
    @POST("api/auth/login") suspend fun login(@Body datos: Login): Acceso
    @POST("api/auth/register") suspend fun registrar(@Body datos: Registro): Mensaje
    @POST("api/auth/verificar") suspend fun verificar(@Body datos: Verificacion): Acceso
    @POST("api/auth/reenviar-codigo") suspend fun reenviar(@Body datos: Correo): Mensaje
    @GET("api/legal/documentos") suspend fun documentos(): Documentos
    @GET("api/perfil") suspend fun perfil(): Usuario
    @GET("api/perfil/objetos") suspend fun propios(): List<Objeto>
    @GET("api/coincidencias/{id}") suspend fun pareja(@Path("id") id: String): Pareja
    @POST("api/coincidencias/{id}/descartar") suspend fun descartar(@Path("id") id: String): EstadoPareja
    @POST("api/coincidencias/{id}/confirmar") suspend fun recuperar(@Path("id") id: String, @Body datos: ConfirmarRecuperacion): Recuperacion
    @GET("api/notificaciones") suspend fun avisos(): List<Aviso>
    @PUT("api/notificaciones/{id}/leida") suspend fun leer(@Path("id") id: Long): Aviso
    @GET("api/notificaciones/preferencias") suspend fun preferencias(): Preferencias
    @PUT("api/notificaciones/preferencias") suspend fun guardarPreferencias(@Body datos: Preferencias): Preferencias
    @POST("api/objetos/{id}/reportes") suspend fun reportar(@Path("id") id: Long, @Body datos: Denuncia): ResultadoReporte
    @GET("api/perfil/solicitud-eliminacion") suspend fun eliminacion(): Eliminacion
    @POST("api/perfil/solicitud-eliminacion") suspend fun solicitarEliminacion(@Body datos: ConfirmarEliminacion): Eliminacion
    // H35: contrato de integración propuesto; la API es parte de la entrega de Yajaira.
    @POST("api/auth/recuperar-password") suspend fun solicitarClave(@Body datos: Correo): Mensaje
    @POST("api/auth/restablecer-password") suspend fun restablecer(@Body datos: Restablecer): Mensaje
}
