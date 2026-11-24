package com.proyectointegrador.movil.data.repository

import com.proyectointegrador.movil.domain.*
import com.proyectointegrador.movil.model.*
import com.proyectointegrador.movil.data.remote.ApiService
import com.proyectointegrador.movil.data.session.Sesion
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.RequestBody.Companion.toRequestBody
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

interface AlmacenSesion {
    val cambios: Flow<EstadoSesion>
    val revision: Long
    val autenticado: Boolean
    val aviso: String?
    fun guardar(token: String)
    fun cerrar()
    fun cerrarConAviso(mensaje:String) { cerrar() }
}

object SesionCifrada : AlmacenSesion {
    override val cambios get() = Sesion.estado
    override val revision get() = Sesion.revision
    override val autenticado get() = Sesion.actual!=null
    override val aviso get() = Sesion.aviso.value
    override fun guardar(token:String) = Sesion.guardar(token)
    override fun cerrar() = Sesion.cerrar()
    override fun cerrarConAviso(mensaje:String) = Sesion.cerrar(aviso=mensaje)
}

data class ResultadoCola(val enviadas:Int,val rechazadas:Int,val reintentar:Boolean)
data class SugerenciasDatos(val objeto:Objeto,val candidatas:List<CoincidenciaSugerida>,val usuario:Usuario)
data class ParejaDatos(val pareja:Pareja,val usuario:Usuario?)

/** Datos privados solo en memoria: no se escriben en Room. */
class CuentaRepository(private val api:ApiService, private val publicaciones:PublicacionesRepository,
                       private val sesion:AlmacenSesion = SesionCifrada,
                       private val biblioteca:com.proyectointegrador.movil.data.remote.BibliotecaApi? = null,
                       private val externa:com.proyectointegrador.movil.data.push.SesionExterna? = null,
                       private val chatLocal:com.proyectointegrador.movil.data.local.AlmacenChat? = null,
                       private val imagenes:ImagenesPrivadas = ImagenesPrivadas(),
                       private val cola:com.proyectointegrador.movil.data.local.ColaPublicaciones? = null,
                       private val alEncolarPublicacion:(()->Unit)? = null) {
    private val noLeidos=mutableSetOf<Long>()
    private val _sinLeer=MutableStateFlow<Int?>(null);val sinLeer=_sinLeer.asStateFlow()
    val cambiosSesion get()=sesion.cambios
    val estadoSesion get()=EstadoSesion(sesion.autenticado,sesion.aviso,sesion.revision)
    private suspend fun <T> privada(bloque:suspend()->T):T = withContext(Dispatchers.IO) {
        val revision=sesion.revision
        if(!sesion.autenticado) throw ErrorDatos("Inicia sesión para continuar.",401)
        val resultado=llamadaApi(bloque)
        if(revision!=sesion.revision) throw CancellationException("La sesión cambió")
        resultado
    }
    private suspend fun acceso(bloque:suspend()->Acceso)=withContext(Dispatchers.IO) {
        val revision=sesion.revision
        val respuesta=llamadaApi(bloque)
        if(revision!=sesion.revision) throw CancellationException("La sesión cambió")
        publicaciones.invalidar()
        chatLocal?.limpiar();imagenes.limpiar();cola?.limpiar()
        if(revision!=sesion.revision) throw CancellationException("La sesión cambió")
        synchronized(noLeidos){noLeidos.clear();_sinLeer.value=null}
        sesion.guardar(respuesta.token)
        // H57 y H72: complementos de Firebase. No bloquean ni hacen fallar el inicio de sesión.
        externa?.let {runCatching {it.alEntrar()}.onFailure {error->if(error is CancellationException)throw error}}
    }
    /** Se llama al abrir la app con una sesión guardada. */
    suspend fun alAbrirApp() {if(sesion.autenticado) externa?.alEntrar()}
    suspend fun login(correo:String,password:String)=acceso {api.login(Login(correo.trim(),password))}
    suspend fun verificar(correo:String,codigo:String)=acceso {api.verificar(Verificacion(correo.trim(),codigo))}
    suspend fun registrar(datos:Registro)=llamadaApi {api.registrar(datos)}
    suspend fun documentos()=llamadaApi {api.documentos()}
    suspend fun reenviar(correo:String)=llamadaApi {api.reenviar(Correo(correo.trim()))}
    suspend fun actualizarPerfil(datos:PerfilRequest)=privada {api.actualizarPerfil(datos)}
    suspend fun perfil()=privada {api.perfil()}
    suspend fun crearInvitacion() = privada { api.crearInvitacion() }
    suspend fun propios()=privada {api.propios()}
    suspend fun objeto(id:Long)=privada {api.objetoPorId(id)}
    suspend fun sugerirPublicacion(datos: SolicitudSugerencia) = privada { api.sugerirPublicacion(datos) }
    suspend fun publicar(datos:PublicacionRequest,idCliente:String?=null)=privada {val respuesta=api.publicar(datos,idCliente);publicaciones.invalidar();respuesta}
    /**
     * H65: publica; si no hay conexión y no lleva foto, la deja en la cola y devuelve null (se enviará sola al volver la red).
     * La [idCliente] se crea antes del primer intento: si la respuesta se pierde, el reintento no duplica la publicación.
     */
    suspend fun publicarOEncolar(datos:PublicacionRequest,idCliente:String,llevaFoto:Boolean):Long? {
        try {
            return publicar(datos,idCliente).id
        } catch(e:ErrorDatos) {
            if(e.estadoHttp!=null||cola==null) throw e
            if(llevaFoto) throw ErrorDatos("Sin conexión. Las publicaciones con foto necesitan conexión; quita la foto para guardarla y enviarla cuando vuelva la red.")
            if(!sesion.autenticado) throw e
            cola.agregar(idCliente,datos)
            alEncolarPublicacion?.invoke()
            return null
        }
    }
    suspend fun publicacionesPendientes()=cola?.pendientes().orEmpty()
    /** Envía en orden lo escrito sin conexión. Se detiene en el primer fallo de red; descarta lo que el servidor rechaza (400 a 499, salvo 401 y 429). */
    suspend fun enviarPublicacionesPendientes():ResultadoCola {
        val almacen=cola?:return ResultadoCola(0,0,false)
        if(!sesion.autenticado) return ResultadoCola(0,0,false)
        var enviadas=0;var rechazadas=0
        for(pendiente in almacen.pendientes()) {
            try {
                publicar(pendiente.datos,pendiente.idCliente)
                almacen.quitar(pendiente.localId);enviadas++
            } catch(e:CancellationException) {
                throw e
            } catch(e:Exception) {
                val error=e.comoErrorDatos()
                val codigo=error.estadoHttp
                when {
                    codigo==null||codigo>=500||codigo==429 -> return ResultadoCola(enviadas,rechazadas,true)
                    codigo==401 -> return ResultadoCola(enviadas,rechazadas,false)
                    else -> {almacen.quitar(pendiente.localId);rechazadas++}
                }
            }
        }
        return ResultadoCola(enviadas,rechazadas,false)
    }
    suspend fun subirFoto(id:Long,foto:FotoPreparada)=privada {val parte=okhttp3.MultipartBody.Part.createFormData("archivo",foto.nombre,foto.bytes.toRequestBody(foto.tipo.toMediaType()));val respuesta=api.subirFoto(id,parte);publicaciones.invalidar();respuesta}
    suspend fun editar(id:Long,datos:PublicacionRequest)=privada {val respuesta=api.editar(id,datos);publicaciones.invalidar();respuesta}
    suspend fun eliminar(id:Long)=privada {
        val respuesta=api.eliminar(id)
        if(!respuesta.isSuccessful)throw retrofit2.HttpException(respuesta)
        publicaciones.invalidar()
    }
    suspend fun sugerencias(id:Long)=privada {SugerenciasDatos(api.objetoPorId(id),api.coincidencias(id),api.perfil())}
    suspend fun pareja(id:String):ParejaDatos=llamadaApi {
        val revision=sesion.revision
        val pareja=api.pareja(id)
        val usuario=if(sesion.autenticado) privada {api.perfil()} else null
        if(revision!=sesion.revision) throw CancellationException("La sesión cambió")
        ParejaDatos(pareja,usuario)
    }
    suspend fun qrRetiroPropio(id: Long) = privada { api.qrRetiroPropio(id) }

    suspend fun registrarRetiro(token: String, etiqueta: String) = privada {
        val servicio = biblioteca ?: throw ErrorDatos("La API de biblioteca no está configurada.")
        val retiro = servicio.retirar(RetiroCustodia(token, etiqueta))
        publicaciones.invalidar()
        retiro
    }

    suspend fun miReclamoCustodia(id: Long) = privada { api.miReclamoCustodia(id) }
    suspend fun reclamarCustodia(id: Long, respuesta: String) = privada {
        api.reclamarCustodia(id, RespuestaCustodia(respuesta))
    }

    suspend fun contactar(datos:ContactoRequest)=privada {api.contactar(datos)}
    /** H62 y H64: foto privada del chat. Rechaza cualquier ruta que no sea de nuestra API para no filtrar el token. */
    suspend fun fotoPrivada(ruta:String):ByteArray {
        if(!esRutaFotoPrivada(ruta)) throw ErrorDatos("Esta foto no está disponible.")
        imagenes.obtener(ruta)?.let {return it}
        val bytes=privada {api.descargarPrivado(ruta.removePrefix("/")).bytes()}
        imagenes.guardar(ruta,bytes)
        return bytes
    }
    // H64: foto de perfil. Devuelve null si la cuenta no tiene foto (404).
    suspend fun fotoPerfil():ByteArray? = try {privada {api.fotoPerfil().bytes()}} catch(e:ErrorDatos) {if(e.estadoHttp==404) null else throw e}
    suspend fun subirFotoPerfil(foto:FotoPreparada)=privada {
        val parte=okhttp3.MultipartBody.Part.createFormData("archivo",foto.nombre,foto.bytes.toRequestBody(foto.tipo.toMediaType()))
        api.subirFotoPerfil(parte)
    }
    suspend fun quitarFotoPerfil()=privada {api.quitarFotoPerfil();Unit}
    // H55 a H63: chat. Cada llamada exige sesión y descarta la respuesta si la sesión cambió mientras viajaba.
    suspend fun iniciarConversacion(datos:IniciarConversacion)=privada {api.iniciarConversacion(datos)}
    /** Con conexión guarda la lista; sin conexión devuelve la última copia guardada (si la hay), marcada como tal. */
    /** H71: público, sin sesión. No se guarda en ninguna caché: un aviso vencido no debe seguir apareciendo. */
    suspend fun avisosDestacados()=llamadaApi {api.avisosDestacados()}
    suspend fun conversacion(id:Long)=privada {api.conversacion(id)}
    suspend fun conversaciones():Lectura<List<Conversacion>> {
        try {
            val lista=privada {api.conversaciones()}
            chatLocal?.guardarConversaciones(lista)
            return Lectura(lista)
        } catch(e:ErrorDatos) {
            if(e.estadoHttp!=null||chatLocal==null) throw e
            val guardada=chatLocal.conversaciones()
            if(guardada.isEmpty()) throw e
            return Lectura(guardada,desdeCache=true,aviso="Sin conexión. Mostrando tus conversaciones guardadas.")
        }
    }
    suspend fun mensajes(id:Long,despuesDe:Long)=privada {api.mensajes(id,despuesDe)}
    /** H65: con [idCliente] el servidor devuelve el mismo mensaje si el envío se repite (reintento tras perder la respuesta). */
    suspend fun enviarMensaje(id:Long,texto:String,idCliente:String?=null)=privada {api.enviarMensaje(id,EnviarMensaje(texto),idCliente)}
    suspend fun enviarFotoChat(id:Long,texto:String?,foto:FotoPreparada)=privada {
        val parte=okhttp3.MultipartBody.Part.createFormData("foto",foto.nombre,foto.bytes.toRequestBody(foto.tipo.toMediaType()))
        api.enviarMensajeConFoto(id,parte,texto?.takeIf {it.isNotBlank()}?.toRequestBody("text/plain".toMediaType()))
    }
    suspend fun marcarLeida(id:Long)=privada {api.marcarConversacionLeida(id);Unit}
    suspend fun bloquearConversacion(id:Long)=privada {api.bloquearConversacion(id)}
    suspend fun desbloquearConversacion(id:Long)=privada {api.desbloquearConversacion(id)}
    suspend fun reportarMensaje(id:Long,mensajeId:Long,motivo:String)=privada {api.reportarMensaje(id,mensajeId,ReportarMensaje(motivo));Unit}
    suspend fun descartar(id:String)=privada {val respuesta=api.descartar(id);publicaciones.invalidar();respuesta}
    suspend fun recuperar(id:String)=privada {val respuesta=api.recuperar(id,ConfirmarRecuperacion());publicaciones.invalidar();respuesta}
    suspend fun avisos()=privada {api.avisos()}.also {avisos->synchronized(noLeidos){noLeidos.clear();noLeidos.addAll(avisos.filter {!it.leida}.map {it.id});_sinLeer.value=noLeidos.size}}
    suspend fun leer(id:Long)=privada {api.leer(id)}.also {aviso->synchronized(noLeidos){if(aviso.leida)noLeidos.remove(id) else noLeidos.add(id);_sinLeer.value=noLeidos.size}}
    suspend fun novedadesReclamos(desde: String, despuesDe: Long) = privada { api.novedadesReclamos(desde, despuesDe) }

    suspend fun historialAvisos(filtro: FiltroAvisos, pagina: Int) = privada {
        api.historialAvisos(filtro.name, pagina)
    }

    suspend fun leerTodosLosAvisos() = privada { api.leerTodosLosAvisos() }.also {
        synchronized(noLeidos) { noLeidos.clear(); _sinLeer.value = it.noLeidas }
    }

    suspend fun preferencias()=privada {api.preferencias()}
    suspend fun guardarPreferencias(datos:Preferencias)=privada {api.guardarPreferencias(datos)}
    suspend fun eliminacion()=privada {api.eliminacion()}
    suspend fun solicitarEliminacion()=privada {api.solicitarEliminacion(ConfirmarEliminacion())}
    suspend fun objetoParaReporte(id:Long)=privada {api.objetoPorId(id)}
    suspend fun reportar(id:Long,datos:Denuncia)=privada {api.reportar(id,datos)}
    suspend fun solicitarClave(correo:String)=llamadaApi {api.solicitarClave(Correo(correo.trim()))}
    suspend fun restablecer(token:String,password:String)=llamadaApi {
        api.restablecer(Restablecer(token,password))
        if(sesion.autenticado) cerrarSesion("Tu contraseña se actualizó. Inicia sesión de nuevo.")
        else publicaciones.invalidar()
    }
    suspend fun salir(todos: Boolean) {
        val revision = sesion.revision
        // Primero se da de baja el teléfono: después de revocar el JWT ya no habría sesión para pedirlo.
        externa?.let {try {it.alSalir()} catch (error:CancellationException) {throw error} catch (_:Exception) {}}
        try {
            privada { if (todos) api.revocarTodasLasSesiones() else api.revocarSesion() }
        } catch (error: ErrorDatos) {
            if (error.estadoHttp != 401) throw error
        }
        if (revision == sesion.revision) cerrarSesion()
    }

    suspend fun cerrarSesion(aviso:String?=null) = withContext(Dispatchers.IO) {
        try {publicaciones.invalidar();chatLocal?.limpiar();imagenes.limpiar();cola?.limpiar()} finally {synchronized(noLeidos){noLeidos.clear();_sinLeer.value=null};if(aviso==null)sesion.cerrar() else sesion.cerrarConAviso(aviso)}
    }
}
