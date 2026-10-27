package com.proyectointegrador.movil.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.proyectointegrador.movil.data.repository.comoErrorDatos
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class EstadoConsulta<T>(val datos:T?=null,val cargando:Boolean=true,val error:String?=null)
data class EstadoAccion(val ocupada:Boolean=false,val error:String?=null,val mensaje:String?=null)

open class AccionViewModel : ViewModel() {
    private val _accion=MutableStateFlow(EstadoAccion())
    val accion=_accion.asStateFlow()
    private val _evento=MutableStateFlow<String?>(null)
    val evento=_evento.asStateFlow()
    fun consumirEvento() {_evento.value=null}
    protected fun emitirEvento(valor:String) {_evento.value=valor}
    protected fun informar(mensaje:String) {_accion.value=_accion.value.copy(mensaje=mensaje)}
    protected fun rechazar(mensaje:String) {_accion.value=EstadoAccion(error=mensaje)}
    protected fun ejecutar(bloque:suspend()->Unit) {
        if(_accion.value.ocupada) return
        _accion.value=EstadoAccion(ocupada=true)
        viewModelScope.launch {
            try {bloque();_accion.value=_accion.value.copy(ocupada=false)}
            catch(e:CancellationException) {throw e}
            catch(e:Exception) {_accion.value=EstadoAccion(error=e.comoErrorDatos().mensaje)}
        }
    }
}

open class CargaViewModel<T>(private val cargar:suspend()->T) : AccionViewModel() {
    private val _estado=MutableStateFlow(EstadoConsulta<T>())
    val estado=_estado.asStateFlow()
    private var carga:Job?=null
    init {recargar()}
    fun recargar() {
        carga?.cancel();_estado.value=EstadoConsulta()
        carga=viewModelScope.launch {
            try {_estado.value=EstadoConsulta(datos=cargar(),cargando=false)}
            catch(e:CancellationException){throw e}
            catch(e:Exception){_estado.value=EstadoConsulta(cargando=false,error=e.comoErrorDatos().mensaje)}
        }
    }
    protected fun actualizar(dato:T) {_estado.value=EstadoConsulta(datos=dato,cargando=false)}
}
