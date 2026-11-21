package com.proyectointegrador.movil.data.remote

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

    @POST("api/asistente/buscar-por-foto")
    suspend fun buscarPorFoto(@Body datos: FotoBusquedaRequest): FotoBusquedaRespuesta

    @POST("api/asistente/interpretar")
    suspend fun interpretarFindi(@Body datos: ConsultaFindi): InterpretacionFindi

    @POST("api/asistente/sugerir-publicacion")
    suspend fun sugerirPublicacion(@Body datos: SolicitudSugerencia): SugerenciaFindi

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
        @Query("categoriaId") categoriaId: Long? = null,
        @Query("ubicacion") ubicacion: String? = null,
        @Query("desde") desde: String? = null,
        @Query("hasta") hasta: String? = null,
        @Query("latitud") latitud: Double? = null,
        @Query("longitud") longitud: Double? = null,
        @Query("radioKm") radioKm: Double? = null
    ): List<Objeto>

    @GET("api/objetos/categoria/{id}")
    suspend fun objetosPorCategoria(@Path("id") id: Long): List<Objeto>

    @GET("api/categorias")
    suspend fun categorias(): List<Categoria>

    @GET("api/objetos/{id}/coincidencias")
    suspend fun coincidencias(@Path("id") id: Long): List<CoincidenciaSugerida>

    @retrofit2.http.Multipart @POST("api/objetos/{id}/fotos") suspend fun subirFoto(@Path("id") id:Long,@retrofit2.http.Part foto:okhttp3.MultipartBody.Part):ObjetoFoto
    @POST("api/objetos") suspend fun publicar(@Body datos:PublicacionRequest):Objeto
    @PUT("api/objetos/{id}") suspend fun editar(@Path("id") id:Long,@Body datos:PublicacionRequest):Objeto
    @DELETE("api/objetos/{id}") suspend fun eliminar(@Path("id") id:Long):retrofit2.Response<Unit>
    @POST("api/auth/login") suspend fun login(@Body datos: Login): Acceso
    @POST("api/auth/logout") suspend fun revocarSesion(): Mensaje
    @POST("api/auth/logout-all") suspend fun revocarTodasLasSesiones(): Mensaje
    @POST("api/auth/register") suspend fun registrar(@Body datos: Registro): Mensaje
    @POST("api/auth/verificar") suspend fun verificar(@Body datos: Verificacion): Acceso
    @POST("api/auth/reenviar-codigo") suspend fun reenviar(@Body datos: Correo): Mensaje
    @GET("api/legal/documentos") suspend fun documentos(): Documentos
    @PUT("api/perfil") suspend fun actualizarPerfil(@Body datos:PerfilRequest):Usuario
    @GET("api/perfil") suspend fun perfil(): Usuario
    @POST("api/perfil/invitaciones") suspend fun crearInvitacion(): EnlaceInvitacion
    @GET("api/perfil/objetos") suspend fun propios(): List<Objeto>
    @GET("api/objetos/{id}/reclamos/custodia")
    suspend fun miReclamoCustodia(@Path("id") id: Long): ReclamoCustodia

    @GET("api/objetos/{id}/reclamos/custodia/qr-retiro")
    suspend fun qrRetiroPropio(@Path("id") id: Long): QrRetiroPropio

    @POST("api/objetos/{id}/reclamos/custodia")
    suspend fun reclamarCustodia(@Path("id") id: Long, @Body datos: RespuestaCustodia): ReclamoCustodia

    @POST("api/contactos") suspend fun contactar(@Body datos:ContactoRequest):ContactoResultado
    @GET("api/coincidencias/{id}") suspend fun pareja(@Path("id") id: String): Pareja
    @POST("api/coincidencias/{id}/descartar") suspend fun descartar(@Path("id") id: String): EstadoPareja
    @POST("api/coincidencias/{id}/confirmar") suspend fun recuperar(@Path("id") id: String, @Body datos: ConfirmarRecuperacion): Recuperacion
    @GET("api/notificaciones") suspend fun avisos(): List<Aviso>
    @GET("api/notificaciones/reclamos/novedades")
    suspend fun novedadesReclamos(@Query("desde") desde: String, @Query("despuesDe") despuesDe: Long): List<Aviso>

    @GET("api/notificaciones/historial")
    suspend fun historialAvisos(@Query("estado") estado: String, @Query("pagina") pagina: Int): HistorialAvisos

    @PUT("api/notificaciones/leidas") suspend fun leerTodosLosAvisos(): AvisosLeidos

    @PUT("api/notificaciones/{id}/leida") suspend fun leer(@Path("id") id: Long): Aviso
    @GET("api/notificaciones/preferencias") suspend fun preferencias(): Preferencias
    @PUT("api/notificaciones/preferencias") suspend fun guardarPreferencias(@Body datos: Preferencias): Preferencias
    @POST("api/objetos/{id}/reportes") suspend fun reportar(@Path("id") id: Long, @Body datos: Denuncia): ResultadoReporte
    @GET("api/perfil/solicitud-eliminacion") suspend fun eliminacion(): Eliminacion
    @POST("api/perfil/solicitud-eliminacion") suspend fun solicitarEliminacion(@Body datos: ConfirmarEliminacion): Eliminacion
    // H35: recuperación mediante la API común, con enlace de un solo uso.
    @POST("api/auth/recuperar-password") suspend fun solicitarClave(@Body datos: Correo): Mensaje
    @POST("api/auth/restablecer-password") suspend fun restablecer(@Body datos: Restablecer): Mensaje
    // H57: dispositivo para recibir push. H72: token personalizado para Firebase Auth.
    @POST("api/dispositivos") suspend fun registrarDispositivo(@Body datos: DispositivoRequest): Dispositivo
    @DELETE("api/dispositivos/{id}") suspend fun eliminarDispositivo(@Path("id") id: Long): retrofit2.Response<Unit>
    @POST("api/auth/firebase-token") suspend fun tokenFirebase(): TokenFirebase
    // H64: foto de perfil privada. No es pública: se pide con sesión.
    @GET("api/perfil/foto") suspend fun fotoPerfil(): okhttp3.ResponseBody
    @retrofit2.http.Multipart
    @POST("api/perfil/foto") suspend fun subirFotoPerfil(@retrofit2.http.Part archivo: okhttp3.MultipartBody.Part): FotoPerfilRespuesta
    @DELETE("api/perfil/foto") suspend fun quitarFotoPerfil(): retrofit2.Response<Unit>
    // H55 a H63: chat entre personas y con la biblioteca.
    @POST("api/conversaciones") suspend fun iniciarConversacion(@Body datos: IniciarConversacion): Conversacion
    @GET("api/conversaciones") suspend fun conversaciones(): List<Conversacion>
    @GET("api/conversaciones/{id}") suspend fun conversacion(@Path("id") id: Long): Conversacion
    @GET("api/conversaciones/{id}/mensajes") suspend fun mensajes(@Path("id") id: Long, @Query("despuesDe") despuesDe: Long = 0): MensajesRespuesta
    @POST("api/conversaciones/{id}/mensajes") suspend fun enviarMensaje(@Path("id") id: Long, @Body datos: EnviarMensaje): MensajeChat
    @retrofit2.http.Multipart
    @POST("api/conversaciones/{id}/mensajes")
    suspend fun enviarMensajeConFoto(@Path("id") id: Long, @retrofit2.http.Part foto: okhttp3.MultipartBody.Part,
        @retrofit2.http.Part("texto") texto: okhttp3.RequestBody?): MensajeChat
    @POST("api/conversaciones/{id}/leida") suspend fun marcarConversacionLeida(@Path("id") id: Long): retrofit2.Response<Unit>
    @POST("api/conversaciones/{id}/bloqueo") suspend fun bloquearConversacion(@Path("id") id: Long): Conversacion
    @DELETE("api/conversaciones/{id}/bloqueo") suspend fun desbloquearConversacion(@Path("id") id: Long): Conversacion
    @POST("api/conversaciones/{id}/mensajes/{mensajeId}/reporte")
    suspend fun reportarMensaje(@Path("id") id: Long, @Path("mensajeId") mensajeId: Long, @Body datos: ReportarMensaje): retrofit2.Response<Unit>
}
