package com.proyectointegrador.movil.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.proyectointegrador.movil.model.Categoria
import com.proyectointegrador.movil.model.CoincidenciaSugerida
import com.proyectointegrador.movil.model.Objeto
import com.proyectointegrador.movil.repository.ObjetoRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class ObjetosUiState(
    val objetos: List<Objeto> = emptyList(),
    val cargando: Boolean = false,
    val error: String? = null
)

class ObjetosViewModel : ViewModel() {

    private val repository = ObjetoRepository()

    private val _state = MutableStateFlow(ObjetosUiState())
    val state: StateFlow<ObjetosUiState> = _state.asStateFlow()

    private val _detalle = MutableStateFlow<Objeto?>(null)
    val detalle: StateFlow<Objeto?> = _detalle.asStateFlow()

    private val _coincidencias = MutableStateFlow<List<CoincidenciaSugerida>>(emptyList())
    val coincidencias: StateFlow<List<CoincidenciaSugerida>> = _coincidencias.asStateFlow()

    private val _categorias = MutableStateFlow<List<Categoria>>(emptyList())
    val categorias: StateFlow<List<Categoria>> = _categorias.asStateFlow()

    private val _cargandoDetalle = MutableStateFlow(false)
    val cargandoDetalle: StateFlow<Boolean> = _cargandoDetalle.asStateFlow()

    private val _errorDetalle = MutableStateFlow<String?>(null)
    val errorDetalle: StateFlow<String?> = _errorDetalle.asStateFlow()

    init {
        cargarRecientes()
    }

    fun cargarRecientes() {
        viewModelScope.launch {
            _state.value = ObjetosUiState(cargando = true)
            repository.listarObjetos()
                .onSuccess { lista ->
                    _state.value = ObjetosUiState(objetos = lista, cargando = false)
                }
                .onFailure { error ->
                    _state.value = ObjetosUiState(cargando = false, error = error.mensaje())
                }
        }
    }

    fun buscar(q: String, tipo: String? = null) {
        viewModelScope.launch {
            _state.value = ObjetosUiState(cargando = true)
            repository.buscar(q = q.ifBlank { null }, tipo = tipo)
                .onSuccess { lista ->
                    _state.value = ObjetosUiState(objetos = lista, cargando = false)
                }
                .onFailure { error ->
                    _state.value = ObjetosUiState(cargando = false, error = error.mensaje())
                }
        }
    }

    fun cargarDetalle(id: Long) {
        _cargandoDetalle.value = true
        _errorDetalle.value = null
        _detalle.value = null
        _coincidencias.value = emptyList()

        viewModelScope.launch {
            repository.detalle(id)
                .onSuccess { objeto -> _detalle.value = objeto }
                .onFailure { error -> _errorDetalle.value = error.mensaje() }

            repository.coincidencias(id)
                .onSuccess { lista -> _coincidencias.value = lista }
                .onFailure { /* las coincidencias son opcionales */ }

            _cargandoDetalle.value = false
        }
    }

    fun cargarCategorias() {
        if (_categorias.value.isNotEmpty()) return
        viewModelScope.launch {
            repository.categorias()
                .onSuccess { lista -> _categorias.value = lista }
                .onFailure { /* el selector de categorias es opcional */ }
        }
    }

    fun limpiarError() {
        _state.value = _state.value.copy(error = null)
    }
}

private fun Throwable.mensaje(): String =
    message?.takeIf { it.isNotBlank() }
        ?: "No se pudo conectar con el servidor. Intenta de nuevo."
