package com.proyectointegrador.movil.ui.viewmodel

import androidx.lifecycle.viewModelScope
import com.proyectointegrador.movil.data.local.AlmacenChat
import com.proyectointegrador.movil.data.local.PendienteChat
import com.proyectointegrador.movil.data.repository.CuentaRepository
import com.proyectointegrador.movil.data.repository.FotografiasRepository
import com.proyectointegrador.movil.data.repository.comoErrorDatos
import com.proyectointegrador.movil.domain.errorMensajeChat
import com.proyectointegrador.movil.domain.fusionarMensajes
import com.proyectointegrador.movil.model.Conversacion
import com.proyectointegrador.movil.model.IniciarConversacion
import com.proyectointegrador.movil.model.MensajeChat
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** H55: lista de mis conversaciones, con el último mensaje y los no leídos. Sin conexión muestra la última copia guardada. */
class ConversacionesViewModel(private val repo: CuentaRepository) :
    CargaViewModel<com.proyectointegrador.movil.domain.Lectura<List<Conversacion>>>({ repo.conversaciones() })

/** Abre (o recupera) la conversación de un contacto o de un reclamo y avisa a qué pantalla ir. */
class IniciarChatViewModel(private val repo: CuentaRepository) : AccionViewModel() {
    fun porContacto(contactoId: Long) = iniciar(IniciarConversacion(contactoId = contactoId))
    fun porReclamo(reclamoId: Long) = iniciar(IniciarConversacion(reclamoId = reclamoId))
    private fun iniciar(datos: IniciarConversacion) = ejecutar {
        emitirEvento("conversacion/" + repo.iniciarConversacion(datos).id)
    }
}

data class EstadoChat(
    val mensajes: List<MensajeChat> = emptyList(),
    /** H65: mensajes escritos sin conexión que todavía no se enviaron. */
    val pendientes: List<PendienteChat> = emptyList(),
    val cargando: Boolean = true,
    val error: String? = null,
    val sinConexion: Boolean = false,
    val soloLectura: Boolean = false,
    val bloqueada: Boolean = false,
    val enviando: Boolean = false,
    val aviso: String? = null,
    val miId: Long? = null
)

/**
 * H55 a H65: una conversación. Consulta cada pocos segundos con el cursor `despuesDe` mientras la pantalla está abierta.
 * Guarda lo recibido en [local] para abrirla sin conexión; si se escribe sin red, el texto queda en la cola de salida y se
 * envía, en orden, cuando vuelve la conexión.
 */
class ChatViewModel(
    private val repo: CuentaRepository,
    private val fotografias: FotografiasRepository,
    private val id: Long,
    private val intervaloMs: Long = 4000,
    private val local: AlmacenChat? = null
) : AccionViewModel() {
    private val _chat = MutableStateFlow(EstadoChat())
    val chat = _chat.asStateFlow()
    private var sondeo: Job? = null
    private var ultimoId = 0L

    init {
        viewModelScope.launch { runCatching { repo.perfil().id }.getOrNull()?.let { id -> _chat.update { it.copy(miId = id) } } }
        iniciarSondeo()
    }

    fun iniciarSondeo() {
        if (sondeo?.isActive == true) return
        sondeo = viewModelScope.launch {
            precargar()
            while (true) {
                consultar()
                delay(intervaloMs)
            }
        }
    }

    fun detenerSondeo() { sondeo?.cancel(); sondeo = null }

    /** Muestra enseguida lo guardado; la consulta al servidor lo completa. */
    private suspend fun precargar() {
        val almacen = local ?: return
        try {
            val guardados = almacen.mensajes(id)
            val pendientes = almacen.pendientes(id)
            if (guardados.isNotEmpty()) ultimoId = maxOf(ultimoId, guardados.last().id)
            _chat.update { it.copy(mensajes = fusionarMensajes(it.mensajes, guardados), pendientes = pendientes, cargando = guardados.isEmpty()) }
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
        }
    }

    private suspend fun consultar() {
        try {
            val respuesta = repo.mensajes(id, ultimoId)
            ultimoId = maxOf(ultimoId, respuesta.ultimoId)
            // update es atómico: el sondeo y un envío en curso nunca se pisan.
            _chat.update {
                it.copy(
                    mensajes = fusionarMensajes(it.mensajes, respuesta.mensajes), cargando = false, error = null, sinConexion = false,
                    soloLectura = respuesta.soloLectura, bloqueada = respuesta.bloqueada
                )
            }
            local?.let { runCatching { it.guardarMensajes(id, _chat.value.mensajes) } }
            if (respuesta.mensajes.isNotEmpty()) runCatching { repo.marcarLeida(id) }
            enviarPendientes()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Un fallo de red no borra lo ya mostrado: se avisa y el siguiente sondeo reintenta.
            val error = e.comoErrorDatos()
            val sinRed = error.estadoHttp == null
            _chat.update {
                it.copy(
                    cargando = false, sinConexion = sinRed,
                    error = if (sinRed && (it.mensajes.isNotEmpty() || it.pendientes.isNotEmpty())) null else error.mensaje
                )
            }
        }
    }

    /** H65: envía en orden lo escrito sin conexión. Se detiene en el primer fallo de red y descarta lo que el servidor rechaza. */
    private suspend fun enviarPendientes() {
        val almacen = local ?: return
        for (pendiente in almacen.pendientes(id)) {
            try {
                val nuevo = repo.enviarMensaje(id, pendiente.texto)
                almacen.quitarPendiente(pendiente.localId)
                ultimoId = maxOf(ultimoId, nuevo.id)
                _chat.update { it.copy(mensajes = fusionarMensajes(it.mensajes, listOf(nuevo)), pendientes = it.pendientes.filterNot { p -> p.localId == pendiente.localId }) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val error = e.comoErrorDatos()
                if (error.estadoHttp == null) return
                // El servidor lo rechazó (bloqueo, solo lectura, límite): reintentar no cambiaría nada.
                almacen.quitarPendiente(pendiente.localId)
                _chat.update { it.copy(pendientes = it.pendientes.filterNot { p -> p.localId == pendiente.localId }, aviso = "No se envió un mensaje: ${error.mensaje}") }
            }
        }
        runCatching { almacen.guardarMensajes(id, _chat.value.mensajes) }
    }

    fun reintentar() { _chat.update { it.copy(cargando = it.mensajes.isEmpty(), error = null) }; viewModelScope.launch { consultar() } }

    fun enviar(texto: String, alEnviar: () -> Unit = {}) {
        val estado = _chat.value
        if (estado.enviando || estado.soloLectura || estado.bloqueada) return
        errorMensajeChat(texto)?.let { error -> _chat.update { it.copy(aviso = error) }; return }
        _chat.update { it.copy(enviando = true, aviso = null) }
        viewModelScope.launch {
            try {
                val nuevo = repo.enviarMensaje(id, texto.trim())
                ultimoId = maxOf(ultimoId, nuevo.id)
                _chat.update { it.copy(mensajes = fusionarMensajes(it.mensajes, listOf(nuevo)), enviando = false) }
                alEnviar()
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val error = e.comoErrorDatos()
                val almacen = local
                if (error.estadoHttp == null && almacen != null) {
                    // Sin conexión: el texto se guarda y se enviará solo cuando vuelva la red.
                    val pendiente = almacen.agregarPendiente(id, texto.trim())
                    _chat.update { it.copy(enviando = false, sinConexion = true, pendientes = it.pendientes + pendiente) }
                    alEnviar()
                } else {
                    _chat.update { it.copy(enviando = false, aviso = error.mensaje) }
                }
            }
        }
    }

    /** H62: la foto se valida igual que las de publicaciones (JPEG o PNG, 5 MiB). Las fotos necesitan conexión. */
    fun enviarFoto(uri: String, texto: String? = null) {
        val estado = _chat.value
        if (estado.enviando || estado.soloLectura || estado.bloqueada) return
        _chat.update { it.copy(enviando = true, aviso = null) }
        viewModelScope.launch {
            try {
                val foto = fotografias.preparar(uri)
                val nuevo = repo.enviarFotoChat(id, texto, foto)
                ultimoId = maxOf(ultimoId, nuevo.id)
                _chat.update { it.copy(mensajes = fusionarMensajes(it.mensajes, listOf(nuevo)), enviando = false) }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                val error = e.comoErrorDatos()
                val mensaje = if (error.estadoHttp == null) "Sin conexión. Las fotos solo se envían con conexión." else error.mensaje
                _chat.update { it.copy(enviando = false, aviso = mensaje) }
            }
        }
    }

    fun bloquear(activar: Boolean) = ejecutar {
        val c = if (activar) repo.bloquearConversacion(id) else repo.desbloquearConversacion(id)
        _chat.update { it.copy(bloqueada = c.bloqueada, soloLectura = c.soloLectura) }
        informar(if (c.bloqueada) "Bloqueaste esta conversación." else "Desbloqueaste esta conversación.")
    }

    fun reportar(mensajeId: Long, motivo: String) {
        if (motivo.trim().length !in 3..255) { rechazar("Escribe un motivo de 3 a 255 caracteres."); return }
        ejecutar {
            repo.reportarMensaje(id, mensajeId, motivo.trim())
            informar("Gracias. Revisaremos el mensaje.")
        }
    }

    override fun onCleared() { detenerSondeo(); super.onCleared() }
}
