package com.proyectointegrador.movil.ui.viewmodel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.proyectointegrador.movil.data.repository.CuentaRepository
import com.proyectointegrador.movil.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
data class CoincidenciaInicio(val origen:Long,val candidata:CoincidenciaSugerida)
data class ComunidadEstado(val usuario:Usuario?=null,val sinLeer:Int=0,val coincidencias:List<CoincidenciaInicio> = emptyList(),val aviso:String?=null)
class ComunidadViewModel(private val repo:CuentaRepository):ViewModel() {
 private val _estado=MutableStateFlow(ComunidadEstado());val estado=_estado.asStateFlow()
 private var carga:Job?=null
 fun recargar(){carga?.cancel();_estado.value=ComunidadEstado()
  if(!repo.estadoSesion.autenticado)return
  carga=viewModelScope.launch {
   try {
    _estado.value=_estado.value.copy(usuario=repo.perfil())
    _estado.value=_estado.value.copy(sinLeer=repo.avisos().count {!it.leida})
    val candidatos=repo.propios().filter {it.tipo=="PERDIDO"&&it.estado=="ACTIVO"}.take(3).flatMap {o->repo.sugerencias(o.id).candidatas.take(3).map {CoincidenciaInicio(o.id,it)}}
    _estado.value=_estado.value.copy(coincidencias=candidatos)
   }catch(e:CancellationException){throw e}catch(_:Exception){_estado.value=_estado.value.copy(aviso="No se pudo actualizar toda la actividad de tu cuenta.")}
  }
 }
}
