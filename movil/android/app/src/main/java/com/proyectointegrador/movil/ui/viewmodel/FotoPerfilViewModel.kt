package com.proyectointegrador.movil.ui.viewmodel

import androidx.lifecycle.viewModelScope
import com.proyectointegrador.movil.data.repository.CuentaRepository
import com.proyectointegrador.movil.data.repository.FotografiasRepository
import com.proyectointegrador.movil.data.repository.comoErrorDatos
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class EstadoFotoPerfil(val bytes: ByteArray? = null, val cargando: Boolean = true, val error: String? = null)

/** H64: ver, cambiar y quitar la foto de perfil. La foto solo la ve la propia persona (y quien conversa con ella). */
class FotoPerfilViewModel(private val repo: CuentaRepository, private val fotografias: FotografiasRepository) : AccionViewModel() {
    private val _foto = MutableStateFlow(EstadoFotoPerfil())
    val foto = _foto.asStateFlow()

    init { cargar() }

    fun cargar() {
        _foto.value = EstadoFotoPerfil(bytes = _foto.value.bytes, cargando = true)
        viewModelScope.launch {
            try {
                _foto.value = EstadoFotoPerfil(bytes = repo.fotoPerfil(), cargando = false)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _foto.value = EstadoFotoPerfil(bytes = _foto.value.bytes, cargando = false, error = e.comoErrorDatos().mensaje)
            }
        }
    }

    /** La foto se valida como las de publicaciones (JPEG o PNG, hasta 5 MiB) antes de subirla. */
    fun cambiar(uri: String) = ejecutar {
        val preparada = fotografias.preparar(uri)
        repo.subirFotoPerfil(preparada)
        _foto.value = EstadoFotoPerfil(bytes = preparada.bytes, cargando = false)
        informar("Tu foto de perfil se actualizó.")
    }

    fun quitar() = ejecutar {
        repo.quitarFotoPerfil()
        _foto.value = EstadoFotoPerfil(bytes = null, cargando = false)
        informar("Quitaste tu foto de perfil.")
    }
}
