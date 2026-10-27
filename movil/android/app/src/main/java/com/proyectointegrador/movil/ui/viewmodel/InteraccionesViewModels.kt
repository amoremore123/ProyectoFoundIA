package com.proyectointegrador.movil.ui.viewmodel

import com.proyectointegrador.movil.BuildConfig
import com.proyectointegrador.movil.data.repository.*
import com.proyectointegrador.movil.domain.*
import com.proyectointegrador.movil.model.*
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.*

class PropiosViewModel(repo:CuentaRepository):CargaViewModel<List<Objeto>>({repo.propios()})
class SugerenciasViewModel(repo:CuentaRepository,id:Long):CargaViewModel<SugerenciasDatos>({repo.sugerencias(id)}) {
    val puedeGestionar get()=estado.value.datos?.let {it.objeto.publicadoPor?.id==it.usuario.id && it.objeto.tipo=="PERDIDO"}==true
}
class ParejaViewModel(private val repo:CuentaRepository,private val id:String):CargaViewModel<ParejaDatos>({repo.pareja(id)}) {
    private val _confirmacion=MutableStateFlow<String?>(null);val confirmacion=_confirmacion.asStateFlow()
    private val _resultado=MutableStateFlow<String?>(null);val resultado=_resultado.asStateFlow()
    val puedeGestionar get()=estado.value.datos?.let {it.usuario!=null && it.usuario.id==it.pareja.perdido.publicadoPor?.id &&
        it.pareja.perdido.estado=="ACTIVO" && it.pareja.encontrado.estado=="ACTIVO"}==true
    fun pedir(tipo:String){if(puedeGestionar && tipo in listOf("recuperar","descartar"))_confirmacion.value=tipo}
    fun cancelar(){if(!accion.value.ocupada)_confirmacion.value=null}
    fun confirmar(){val tipo=_confirmacion.value?:return
        if(!puedeGestionar){rechazar("Solo el titular del perdido activo puede gestionar la pareja.");return}
        ejecutar {
            _resultado.value=if(tipo=="recuperar") {
                val r=repo.recuperar(id);"Recuperación confirmada. Fecha del servidor: ${r.fechaRecuperacion}."
            } else {repo.descartar(id);"Coincidencia descartada. Se conservará al volver a consultar."}
            _confirmacion.value=null
        }
    }
}
class AvisosViewModel(private val repo:CuentaRepository):CargaViewModel<List<Aviso>>({repo.avisos()}) {
    fun abrir(id:Long){val aviso=estado.value.datos?.find {it.id==id}?:return
        val par=parejaDeEnlace(aviso.enlace)?:return
        ejecutar {if(!aviso.leida)repo.leer(id);emitirEvento(par)}
    }
    fun leer(id:Long){if(estado.value.datos?.any {it.id==id}!=true)return
        ejecutar {val respuesta=repo.leer(id);actualizar(estado.value.datos.orEmpty().map {if(it.id==id)respuesta else it})}
    }
}
class PreferenciasViewModel(private val repo:CuentaRepository):CargaViewModel<Preferencias>({repo.preferencias()}) {
    private val _edicion=MutableStateFlow<Preferencias?>(null);val edicion=_edicion.asStateFlow()
    fun editar(indice:Int,valor:Boolean){val actual=_edicion.value?:estado.value.datos?:return
        _edicion.value=when(indice){0->actual.copy(coincidencias=valor);1->actual.copy(contactos=valor);else->actual.copy(reclamos=valor)}
    }
    fun cancelar(){_edicion.value=null}
    fun recargarPreferencias(){cancelar();recargar()}
    fun guardar(){val datos=_edicion.value?:estado.value.datos?:return
        ejecutar {val guardadas=repo.guardarPreferencias(datos);actualizar(guardadas);_edicion.value=null;informar("Preferencias guardadas.")}
    }
}
class EliminacionViewModel(private val repo:CuentaRepository):CargaViewModel<Eliminacion>({repo.eliminacion()}) {
    private val _confirmacion=MutableStateFlow(false);val confirmacion=_confirmacion.asStateFlow()
    fun pedir(){if(estado.value.datos?.estado=="SIN_SOLICITUD")_confirmacion.value=true}
    fun cancelar(){if(!accion.value.ocupada)_confirmacion.value=false}
    fun confirmar(){if(!_confirmacion.value)return
        ejecutar {actualizar(repo.solicitarEliminacion());_confirmacion.value=false}
    }
}

data class FormReporte(val motivo:String="SPAM",val descripcion:String="",val confirmar:Boolean=false,val resultado:String?=null)
class ReporteViewModel(private val repo:CuentaRepository,private val id:Long):CargaViewModel<Objeto>({repo.objetoParaReporte(id)}) {
    private val _form=MutableStateFlow(FormReporte());val form=_form.asStateFlow()
    fun motivo(valor:String){if(valor in listOf("SPAM","CONTENIDO_INAPROPIADO","INFORMACION_FALSA","OTRO"))_form.value=_form.value.copy(motivo=valor)}
    fun descripcion(valor:String){_form.value=_form.value.copy(descripcion=valor.take(2000))}
    val puedeEnviar get()=_form.value.motivo!="OTRO" || _form.value.descripcion.isNotBlank()
    fun pedir(){if(puedeEnviar && estado.value.datos!=null)_form.value=_form.value.copy(confirmar=true)}
    fun cancelar(){if(!accion.value.ocupada)_form.value=_form.value.copy(confirmar=false)}
    fun confirmar(){val f=_form.value
        if(!f.confirmar || !puedeEnviar)return
        ejecutar {val r=repo.reportar(id,Denuncia(f.motivo,f.descripcion.trim().ifEmpty {null}))
            _form.value=FormReporte(resultado=if(r.repetido) "Ya existe tu denuncia para esta publicación. Se conserva el trámite original."
                else "Denuncia registrada con número ${r.id}. Estado: ${r.estado}.")}
    }
}
class DetalleAccionesViewModel(private val repo:PublicacionesRepository,cuenta:CuentaRepository,private val id:Long):AccionViewModel() {
    val sesion=cuenta.cambiosSesion.stateIn(viewModelScope,SharingStarted.Eagerly,cuenta.estadoSesion)
    fun compartir(){ejecutar {
        val vigente=repo.vigente(id)
        if(vigente.tipo!="PERDIDO" || vigente.estado !in listOf("ACTIVO","RECUPERADO"))throw ErrorDatos("La publicación ya no está disponible para compartir.")
        emitirEvento(enlacePublico(BuildConfig.WEB_URL,vigente.id))
    }}
    fun errorCompartir(){rechazar("No se pudo abrir el selector para compartir.")}
}
