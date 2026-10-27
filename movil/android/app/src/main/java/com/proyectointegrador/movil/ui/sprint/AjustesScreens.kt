package com.proyectointegrador.movil.ui.sprint
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.proyectointegrador.movil.di.fabrica
import com.proyectointegrador.movil.ui.viewmodel.*
import com.proyectointegrador.movil.ui.components.*

@Composable fun AparienciaScreen(volver:()->Unit,vm:ExperienciaViewModel) {
 val estado by vm.estado.collectAsStateWithLifecycle();val accion by vm.accion.collectAsStateWithLifecycle()
 Pagina("A tu manera",volver){Text("Una experiencia cómoda, de día y de noche.",color=MaterialTheme.colorScheme.onSurfaceVariant)
  Panel("Tema de la aplicación"){listOf("sistema" to "Según el dispositivo","claro" to "Claro","oscuro" to "Oscuro").forEach {(clave,texto)->
   OutlinedButton(onClick={vm.tema(clave)},enabled=!accion.ocupada,modifier=Modifier.fillMaxWidth()){RadioButton(selected=estado.tema==clave,onClick=null);Text(texto,Modifier.weight(1f))}
  }};EstadoAccion(accion)
 }
}
@Composable fun PrivacidadScreen(volver:()->Unit,vm:RegistroViewModel=viewModel(factory=fabrica())) {
 val estado by vm.estado.collectAsStateWithLifecycle()
 Pagina("Privacidad y términos",volver){Consulta(estado,vm::recargarDocumentos){docs,_->
  AvisoEstado("Documentos del proyecto: ${docs.estado}. Versión ${docs.version}. Pendientes de revisión institucional.")
  Panel("Términos de uso"){Text(docs.terminos)};Panel("Política de privacidad"){Text(docs.privacidad)}
 }}
}
@Composable fun EditarPerfilScreen(volver:()->Unit,seguridad:Boolean=false,vm:EditarPerfilViewModel=viewModel(factory=fabrica())) {
 val estado by vm.estado.collectAsStateWithLifecycle();val f by vm.form.collectAsStateWithLifecycle();val accion by vm.accion.collectAsStateWithLifecycle()
 Pagina(if(seguridad)"Seguridad" else "Editar perfil",volver){Consulta(estado,vm::recargar){usuario,_->
  Panel(if(seguridad)"Actualiza tu contraseña" else "Así te conoce tu comunidad"){
   if(!seguridad){Campo("Nombre",f.nombre?:usuario.nombre,{vm.editar("nombre",it)},habilitado=!accion.ocupada);Campo("Apellido",f.apellido?:usuario.apellido,{vm.editar("apellido",it)},habilitado=!accion.ocupada);Text(usuario.correo,style=MaterialTheme.typography.bodySmall)}
   else {Campo("Contraseña actual",f.actual,{vm.editar("actual",it)},true,!accion.ocupada);Campo("Nueva contraseña",f.password,{vm.editar("password",it)},true,!accion.ocupada);Campo("Confirmar contraseña",f.confirmar,{vm.editar("confirmar",it)},true,!accion.ocupada);Text("Al menos 8 caracteres, mayúscula, minúscula, número y símbolo.",style=MaterialTheme.typography.bodySmall)}
   EstadoAccion(accion);BotonPrincipal("Guardar cambios",{vm.guardar(seguridad)},!accion.ocupada)
  }
 }}
}
