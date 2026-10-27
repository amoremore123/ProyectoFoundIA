package com.proyectointegrador.movil.data.repository

import com.proyectointegrador.movil.domain.*
import com.proyectointegrador.movil.model.*
import com.proyectointegrador.movil.network.ApiService
import com.proyectointegrador.movil.network.Sesion
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
}

object SesionCifrada : AlmacenSesion {
    override val cambios get() = Sesion.estado
    override val revision get() = Sesion.revision
    override val autenticado get() = Sesion.actual!=null
    override val aviso get() = Sesion.aviso.value
    override fun guardar(token:String) = Sesion.guardar(token)
    override fun cerrar() = Sesion.cerrar()
}

data class SugerenciasDatos(val objeto:Objeto,val candidatas:List<CoincidenciaSugerida>,val usuario:Usuario)
data class ParejaDatos(val pareja:Pareja,val usuario:Usuario?)

/** Datos privados solo en memoria: no se escriben en Room. */
class CuentaRepository(private val api:ApiService, private val publicaciones:PublicacionesRepository,
                       private val sesion:AlmacenSesion = SesionCifrada) {
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
        if(revision!=sesion.revision) throw CancellationException("La sesión cambió")
        sesion.guardar(respuesta.token)
    }
    suspend fun login(correo:String,password:String)=acceso {api.login(Login(correo.trim(),password))}
    suspend fun verificar(correo:String,codigo:String)=acceso {api.verificar(Verificacion(correo.trim(),codigo))}
    suspend fun registrar(datos:Registro)=llamadaApi {api.registrar(datos)}
    suspend fun documentos()=llamadaApi {api.documentos()}
    suspend fun reenviar(correo:String)=llamadaApi {api.reenviar(Correo(correo.trim()))}
    suspend fun actualizarPerfil(datos:PerfilRequest)=privada {api.actualizarPerfil(datos)}
    suspend fun perfil()=privada {api.perfil()}
    suspend fun propios()=privada {api.propios()}
    suspend fun publicar(datos:PublicacionRequest)=privada {val respuesta=api.publicar(datos);publicaciones.invalidar();respuesta}
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
    suspend fun contactar(datos:ContactoRequest)=privada {api.contactar(datos)}
    suspend fun descartar(id:String)=privada {val respuesta=api.descartar(id);publicaciones.invalidar();respuesta}
    suspend fun recuperar(id:String)=privada {val respuesta=api.recuperar(id,ConfirmarRecuperacion());publicaciones.invalidar();respuesta}
    suspend fun avisos()=privada {api.avisos()}
    suspend fun leer(id:Long)=privada {api.leer(id)}
    suspend fun preferencias()=privada {api.preferencias()}
    suspend fun guardarPreferencias(datos:Preferencias)=privada {api.guardarPreferencias(datos)}
    suspend fun eliminacion()=privada {api.eliminacion()}
    suspend fun solicitarEliminacion()=privada {api.solicitarEliminacion(ConfirmarEliminacion())}
    suspend fun objetoParaReporte(id:Long)=privada {api.objetoPorId(id)}
    suspend fun reportar(id:Long,datos:Denuncia)=privada {api.reportar(id,datos)}
    suspend fun solicitarClave(correo:String)=llamadaApi {api.solicitarClave(Correo(correo.trim()))}
    suspend fun restablecer(token:String,password:String)=llamadaApi {
        api.restablecer(Restablecer(token,password));cerrarSesion()
    }
    suspend fun cerrarSesion() = withContext(Dispatchers.IO) {
        try {publicaciones.invalidar()} finally {sesion.cerrar()}
    }
}
