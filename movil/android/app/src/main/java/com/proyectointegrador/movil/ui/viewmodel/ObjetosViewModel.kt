package com.proyectointegrador.movil.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.proyectointegrador.movil.data.repository.*
import com.proyectointegrador.movil.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class ObjetosUiState(val objetos:List<Objeto> = emptyList(),val cargando:Boolean=false,val error:String?=null,val aviso:String?=null)
data class FormBusqueda(val texto:String="",val tipo:String?=null,val categoriaId:Long?=null)

class ObjetosViewModel(private val repository:PublicacionesRepository):ViewModel() {
    private val _state=MutableStateFlow(ObjetosUiState());val state=_state.asStateFlow()
    private val _busqueda=MutableStateFlow(FormBusqueda());val busqueda=_busqueda.asStateFlow()
    private val _detalle=MutableStateFlow<Objeto?>(null);val detalle=_detalle.asStateFlow()
    private val _coincidencias=MutableStateFlow<List<CoincidenciaSugerida>>(emptyList());val coincidencias=_coincidencias.asStateFlow()
    val categorias=repository.observarCategorias().stateIn(viewModelScope,SharingStarted.WhileSubscribed(5000),emptyList())
    private val _errorCategorias=MutableStateFlow<String?>(null);val errorCategorias=_errorCategorias.asStateFlow()
    private val _cargandoDetalle=MutableStateFlow(false);val cargandoDetalle=_cargandoDetalle.asStateFlow()
    private val _errorDetalle=MutableStateFlow<String?>(null);val errorDetalle=_errorDetalle.asStateFlow()
    private val _avisoDetalle=MutableStateFlow<String?>(null);val avisoDetalle=_avisoDetalle.asStateFlow()
    private var consulta:Job?=null;private var consultaDetalle:Job?=null
    fun cargarRecientes(){consultar(false)}
    fun editarBusqueda(texto:String){_busqueda.value=_busqueda.value.copy(texto=texto);consultar(true,300)}
    fun elegirTipo(tipo:String?){_busqueda.value=_busqueda.value.copy(tipo=tipo);consultar(true)}
    fun elegirCategoria(id:Long?){_busqueda.value=_busqueda.value.copy(categoriaId=id);consultar(true)}
    fun buscarActual(){consultar(true)}
    private fun consultar(buscar:Boolean,espera:Long=0) {
        consulta?.cancel();_state.value=ObjetosUiState(cargando=true)
        consulta=viewModelScope.launch {
            try {
                if(espera>0)delay(espera)
                val f=_busqueda.value
                val fuente=if(buscar)repository.buscar(f.texto.ifBlank {null},f.tipo) else repository.listar()
                fuente.collect {_state.value=ObjetosUiState(objetos=it.datos.filter {o->f.categoriaId==null||o.categoria?.id==f.categoriaId},aviso=it.aviso)}
            } catch(e:CancellationException){throw e}
            catch(e:Exception){_state.value=ObjetosUiState(error=e.comoErrorDatos().mensaje)}
        }
    }
    fun cargarDetalle(id:Long) {
        consultaDetalle?.cancel();_detalle.value=null;_coincidencias.value=emptyList();_errorDetalle.value=null;_avisoDetalle.value=null;_cargandoDetalle.value=true
        consultaDetalle=viewModelScope.launch {
            try {
                repository.detalle(id).collect {lectura->_detalle.value=lectura.datos;_avisoDetalle.value=lectura.aviso}
                _cargandoDetalle.value=false
                try {_coincidencias.value=repository.coincidencias(id)}
                catch(e:CancellationException){throw e}
                catch(e:Exception){_avisoDetalle.value=listOfNotNull(_avisoDetalle.value,"No se pudieron actualizar las coincidencias. Reintenta.").joinToString(" ")}
            } catch(e:CancellationException){throw e}
            catch(e:Exception){_errorDetalle.value=e.comoErrorDatos().mensaje;_cargandoDetalle.value=false}
        }
    }
    fun cargarCategorias(){viewModelScope.launch {
        try {val lectura=repository.categorias();_errorCategorias.value=lectura.aviso}
        catch(e:CancellationException){throw e}
        catch(e:Exception){_errorCategorias.value=e.comoErrorDatos().mensaje}
    }}
    fun limpiarError(){_state.value=_state.value.copy(error=null)}
}
