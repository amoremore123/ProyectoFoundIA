package com.proyectointegrador.movil.ui.viewmodel

import androidx.lifecycle.viewModelScope
import com.proyectointegrador.movil.data.repository.ExperienciaRepository
import kotlinx.coroutines.launch

class ExperienciaViewModel(private val repo:ExperienciaRepository):AccionViewModel() {
    val estado=repo.estado
    init {viewModelScope.launch {repo.cargar()}}
    fun completar(destino:String="inicio"){ejecutar {repo.completar();emitirEvento(destino)}}
    fun tema(valor:String){ejecutar {repo.tema(valor)}}
}
