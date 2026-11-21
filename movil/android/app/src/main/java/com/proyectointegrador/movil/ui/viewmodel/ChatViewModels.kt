package com.proyectointegrador.movil.ui.viewmodel

import androidx.lifecycle.viewModelScope
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

/** H55: lista de mis conversaciones, con el último mensaje y los no leídos. */
class ConversacionesViewModel(private val repo: CuentaRepository) : CargaViewModel<List<Conversacion>>({ repo.conversaciones() })

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
    val cargando: Boolean = true,
    val error: String? = null,
    val soloLectura: Boolean = false,
    val bloqueada: Boolean = false,
    val enviando: Boolean = false,
    val aviso: String? = null,
    val miId: Long? = null
)

/**
 * H55 a H63: una conversación. Consulta cada pocos segundos con el cursor `despuesDe` mientras la pantalla está abierta;
 * al cerrarla el sondeo se cancela.
 */
class ChatViewModel(
    private val repo: CuentaRepository,
    private val fotografias: FotografiasRepository,
    private val id: Long,
    private val intervaloMs: Long = 4000
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
            while (true) {
                consultar()
                delay(intervaloMs)
            }
        }
    }

    fun detenerSondeo() { sondeo?.cancel(); sondeo = null }

    private suspend fun consultar() {
        try {
            val respuesta = repo.mensajes(id, ultimoId)
            ultimoId = maxOf(ultimoId, respuesta.ultimoId)
            // update es atómico: el sondeo y un envío en curso nunca se pisan.
            _chat.update {
                it.copy(
                    mensajes = fusionarMensajes(it.mensajes, respuesta.mensajes), cargando = false, error = null,
                    soloLectura = respuesta.soloLectura, bloqueada = respuesta.bloqueada
                )
            }
            if (respuesta.mensajes.isNotEmpty()) runCatching { repo.marcarLeida(id) }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            // Un fallo de red no borra lo ya mostrado: se avisa y el siguiente sondeo reintenta.
            val mensaje = e.comoErrorDatos().mensaje
            _chat.update { it.copy(cargando = false, error = mensaje) }
        }
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
                val mensaje = e.comoErrorDatos().mensaje
                _chat.update { it.copy(enviando = false, aviso = mensaje) }
            }
        }
    }

    /** H62: la foto se valida igual que las de publicaciones (JPEG o PNG, 5 MiB) antes de subirla. */
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
                val mensaje = e.comoErrorDatos().mensaje
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
