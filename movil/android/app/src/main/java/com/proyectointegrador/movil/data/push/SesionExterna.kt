package com.proyectointegrador.movil.data.push

import com.proyectointegrador.movil.data.remote.ApiService
import com.proyectointegrador.movil.model.DispositivoRequest
import kotlinx.coroutines.CancellationException

/** Dónde se recuerda el dispositivo registrado. Solo guarda el id del servidor, nunca datos de la persona. */
interface AlmacenDispositivo {
    var dispositivoId: Long?
    var tokenRegistrado: String?
}

/**
 * H57 y H72: registra el teléfono para recibir push y entra a Firebase con el token personalizado.
 * Es un complemento de la sesión de FoundIA: ningún fallo de aquí puede impedir entrar, salir ni usar la app.
 */
class SesionExterna(
    private val api: ApiService,
    private val firebase: ServicioFirebase,
    private val almacen: AlmacenDispositivo
) {
    /** Se llama al iniciar sesión y al abrir la app con sesión. */
    suspend fun alEntrar() {
        if (!firebase.disponible) return
        registrarDispositivo()
        entrarEnFirebase()
    }

    /** También la usa el servicio de mensajes cuando Firebase renueva el token. */
    suspend fun registrarDispositivo() {
        try {
            val token = firebase.tokenPush() ?: return
            if (token == almacen.tokenRegistrado && almacen.dispositivoId != null) return
            val respuesta = api.registrarDispositivo(DispositivoRequest(token))
            almacen.dispositivoId = respuesta.id
            almacen.tokenRegistrado = token
        } catch (cancelado: CancellationException) {
            throw cancelado
        } catch (_: Exception) {
            // Sin red o con la sesión vencida: se reintenta en la próxima apertura o renovación de token.
        }
    }

    private suspend fun entrarEnFirebase() {
        try {
            val respuesta = api.tokenFirebase()
            firebase.entrarConToken(respuesta.token, respuesta.uid)
        } catch (cancelado: CancellationException) {
            throw cancelado
        } catch (_: Exception) {
            // 503 (Firebase apagado en el servidor), 401 o sin red: se sigue con la sesión normal.
        }
    }

    /** Debe llamarse antes de revocar el JWT, porque borrar el dispositivo necesita la sesión vigente. */
    suspend fun alSalir() {
        val id = almacen.dispositivoId
        try {
            if (id != null) api.eliminarDispositivo(id)
        } catch (cancelado: CancellationException) {
            throw cancelado
        } catch (_: Exception) {
            // El servidor también borra los dispositivos al cerrar la sesión.
        } finally {
            almacen.dispositivoId = null
            almacen.tokenRegistrado = null
            firebase.borrarTokenPush()
            firebase.salir()
        }
    }
}

class AlmacenDispositivoLocal(contexto: android.content.Context) : AlmacenDispositivo {
    private val prefs = contexto.applicationContext.getSharedPreferences("dispositivo_push", android.content.Context.MODE_PRIVATE)
    override var dispositivoId: Long?
        get() = prefs.getLong("id", -1).takeIf { it > 0 }
        set(valor) { prefs.edit().apply { if (valor == null) remove("id") else putLong("id", valor) }.apply() }
    override var tokenRegistrado: String?
        get() = prefs.getString("token", null)
        set(valor) { prefs.edit().apply { if (valor == null) remove("token") else putString("token", valor) }.apply() }
}
