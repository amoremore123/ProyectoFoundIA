package com.proyectointegrador.movil.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.proyectointegrador.movil.data.repository.CuentaRepository
import com.proyectointegrador.movil.domain.*
import com.proyectointegrador.movil.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

data class FormAcceso(val correo:String="",val password:String="")
class SesionViewModel(repo:CuentaRepository):ViewModel() {
    val estado=repo.cambiosSesion.stateIn(viewModelScope,SharingStarted.Eagerly,repo.estadoSesion)
}
class AccesoViewModel(private val repo:CuentaRepository):AccionViewModel() {
    private val _form=MutableStateFlow(FormAcceso());val form=_form.asStateFlow()
    fun correo(valor:String){_form.value=_form.value.copy(correo=valor)}
    fun password(valor:String){_form.value=_form.value.copy(password=valor)}
    fun entrar() {
        val f=_form.value
        if(!correoValido(f.correo) || f.password.isBlank()){rechazar("Ingresa un correo válido y tu contraseña.");return}
        ejecutar {repo.login(f.correo,f.password);_form.value=FormAcceso()}
    }
}

data class FormRegistro(val nombre:String="",val apellido:String="",val correo:String="",val password:String="",val confirmar:String="",val acepta:Boolean=false)
class RegistroViewModel(private val repo:CuentaRepository):CargaViewModel<Documentos>({repo.documentos()}) {
    private val _form=MutableStateFlow(FormRegistro());val form=_form.asStateFlow()
    fun editar(campo:String,valor:String){_form.value=when(campo){
        "nombre"->_form.value.copy(nombre=valor);"apellido"->_form.value.copy(apellido=valor)
        "correo"->_form.value.copy(correo=valor);"password"->_form.value.copy(password=valor)
        "confirmar"->_form.value.copy(confirmar=valor);else->_form.value
    }}
    fun aceptar(valor:Boolean){_form.value=_form.value.copy(acepta=valor)}
    fun recargarDocumentos(){_form.value=_form.value.copy(acepta=false);recargar()}
    fun registrar() {
        val f=_form.value;val docs=estado.value.datos
        if(docs==null || !f.acepta){rechazar("Lee y acepta los documentos de la versión presentada.");return}
        if(f.nombre.isBlank() || f.apellido.isBlank() || !correoValido(f.correo)){rechazar("Completa nombre, apellido y un correo válido.");return}
        if(!passwordValida(f.password) || f.password!=f.confirmar){rechazar("Revisa la seguridad y la confirmación de la contraseña.");return}
        ejecutar {
            repo.registrar(Registro(f.nombre.trim(),f.apellido.trim(),f.correo.trim(),f.password,true,docs.version))
            _form.value=FormRegistro();emitirEvento(f.correo.trim())
        }
    }
}

data class FormVerificacion(val correo:String="",val codigo:String="",val espera:Int=0)
class VerificacionViewModel(private val repo:CuentaRepository,private val reloj:()->Long=System::currentTimeMillis):AccionViewModel() {
    private val _form=MutableStateFlow(FormVerificacion());val form=_form.asStateFlow()
    private var iniciado=false;private var contador:Job?=null
    fun iniciar(correo:String){if(!iniciado){iniciado=true;_form.value=_form.value.copy(correo=correo)}}
    fun correo(valor:String){_form.value=_form.value.copy(correo=valor)}
    fun codigo(valor:String){_form.value=_form.value.copy(codigo=valor.filter(Char::isDigit).take(6))}
    private fun esperar(segundos:Int) {
        contador?.cancel();val limite=reloj()+segundos*1000L
        contador=viewModelScope.launch {
            while(true){val restantes=((limite-reloj()+999)/1000).toInt().coerceAtLeast(0)
                _form.value=_form.value.copy(espera=restantes)
                if(restantes==0)break
                delay(1000)
            }
        }
    }
    fun verificar(){val f=_form.value
        if(!correoValido(f.correo) || f.codigo.length!=6){rechazar("Revisa el correo y el código de seis dígitos.");return}
        ejecutar {repo.verificar(f.correo,f.codigo);_form.value=FormVerificacion()}
    }
    fun reenviar(){val f=_form.value
        if(!correoValido(f.correo) || f.espera>0)return
        ejecutar {
            try {informar(repo.reenviar(f.correo).mensaje);esperar(60)}
            catch(e:ErrorDatos){if(e.estadoHttp==429)esperar(e.esperaSegundos?:60);throw e}
        }
    }
}
class PerfilViewModel(private val repo:CuentaRepository):CargaViewModel<Usuario>({repo.perfil()}) {
    fun salir(){ejecutar {repo.cerrarSesion()}}
}
class RecuperarViewModel(private val repo:CuentaRepository):AccionViewModel() {
    private val _correo=MutableStateFlow("");val correo=_correo.asStateFlow()
    fun editar(valor:String){_correo.value=valor}
    fun solicitar(){val valor=_correo.value
        if(!correoValido(valor)){rechazar("Ingresa un correo válido.");return}
        ejecutar {repo.solicitarClave(valor);informar("Si el correo corresponde a una cuenta habilitada, recibirás un enlace de recuperación.")}
    }
}

data class FormNuevaClave(val password:String="",val confirmar:String="",val terminado:Boolean=false)
class NuevaClaveViewModel(private val repo:CuentaRepository,private val token:String?):AccionViewModel() {
    private val _form=MutableStateFlow(FormNuevaClave());val form=_form.asStateFlow()
    val enlaceValido=token?.matches(Regex("[A-Za-z0-9._~-]{16,2048}"))==true
    fun password(valor:String){_form.value=_form.value.copy(password=valor)}
    fun confirmar(valor:String){_form.value=_form.value.copy(confirmar=valor)}
    fun guardar(){val f=_form.value
        if(!enlaceValido || f.password!=f.confirmar || !passwordValida(f.password)){rechazar("Revisa el enlace y la seguridad de tu nueva contraseña.");return}
        ejecutar {repo.restablecer(token!!,f.password);_form.value=FormNuevaClave(terminado=true)}
    }
}

data class FormPerfil(val nombre:String?=null,val apellido:String?=null,val actual:String="",val password:String="",val confirmar:String="")
class EditarPerfilViewModel(private val repo:CuentaRepository):CargaViewModel<Usuario>({repo.perfil()}) {
 private val _form=MutableStateFlow(FormPerfil());val form=_form.asStateFlow()
 fun editar(campo:String,valor:String){_form.value=when(campo){"nombre"->_form.value.copy(nombre=valor);"apellido"->_form.value.copy(apellido=valor);"actual"->_form.value.copy(actual=valor);"password"->_form.value.copy(password=valor);"confirmar"->_form.value.copy(confirmar=valor);else->_form.value}}
 fun guardar(seguridad:Boolean){val usuario=estado.value.datos?:return;val f=_form.value
  val nombre=if(seguridad)usuario.nombre else f.nombre?:usuario.nombre;val apellido=if(seguridad)usuario.apellido else f.apellido?:usuario.apellido
  if(nombre.isBlank()||apellido.isBlank()||nombre.length>100||apellido.length>100){rechazar("Revisa tu nombre y apellido.");return}
  if(seguridad&&(f.actual.isBlank()||!passwordValida(f.password)||f.password!=f.confirmar)){rechazar("Revisa tu contraseña actual y la nueva contraseña.");return}
  ejecutar {val nuevo=repo.actualizarPerfil(PerfilRequest(nombre.trim(),apellido.trim(),if(seguridad)f.password else null,if(seguridad)f.actual else null));actualizar(nuevo);_form.value=FormPerfil();informar("Cambios guardados en tu cuenta.")}
 }
}
