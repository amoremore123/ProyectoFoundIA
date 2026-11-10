package com.proyectointegrador.movil.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.proyectointegrador.movil.data.repository.AsistenteRepository
import com.proyectointegrador.movil.data.repository.PROCESO_FINDI
import com.proyectointegrador.movil.data.repository.comoErrorDatos
import com.proyectointegrador.movil.model.RespuestaFindi
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class MensajeFindi(val id: Long, val texto: String, val propio: Boolean = false,
    val respuesta: RespuestaFindi? = null, val reintentar: String? = null)
data class ChatFindiEstado(val mensajes: List<MensajeFindi> = listOf(MensajeFindi(0,
    "¡Hola! Soy FINDI, tu asistente de FoundIA. ¿En qué puedo ayudarte hoy?")), val pensando: Boolean = false)

class AsistenteViewModel(private val repo: AsistenteRepository) : ViewModel() {
    private val _estado = MutableStateFlow(ChatFindiEstado())
    val estado = _estado.asStateFlow()
    private var siguienteId = 0L
    private fun agregar(texto: String, propio: Boolean = false, respuesta: RespuestaFindi? = null, reintentar: String? = null) {
        val mensaje = MensajeFindi(++siguienteId, texto, propio, respuesta, reintentar)
        _estado.update { it.copy(mensajes = it.mensajes + mensaje) }
    }
    fun comoFunciona() { if (!_estado.value.pensando) agregar(PROCESO_FINDI) }
    fun enviar(texto: String, repetir: Boolean = false) {
        val limpio = texto.trim()
        if (limpio.isEmpty() || limpio.length > 300 || _estado.value.pensando) return
        if (!repetir) agregar(limpio, propio = true)
        _estado.update { it.copy(pensando = true) }
        viewModelScope.launch {
            try {
                val r = repo.responder(limpio)
                agregar(r.texto, respuesta = r)
            } catch (e: CancellationException) { throw e }
            catch (e: Exception) { agregar("No pude consultar las publicaciones. ${e.comoErrorDatos().mensaje}", reintentar = limpio) }
            finally { _estado.update { it.copy(pensando = false) } }
        }
    }
}
