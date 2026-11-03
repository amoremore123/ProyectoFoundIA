package com.proyectointegrador.movil.ui.sprint
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.*
import androidx.compose.ui.semantics.Role
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.proyectointegrador.movil.di.fabrica
import com.proyectointegrador.movil.ui.viewmodel.*
import com.proyectointegrador.movil.ui.components.*

@Composable fun AccesoScreen(registro:()->Unit,verificar:(String)->Unit,recuperar:()->Unit,vm:AccesoViewModel=viewModel(factory=fabrica()),sesionVm:SesionViewModel=viewModel(factory=fabrica())) {
 val f by vm.form.collectAsStateWithLifecycle();val accion by vm.accion.collectAsStateWithLifecycle();val sesion by sesionVm.estado.collectAsStateWithLifecycle()
 val experiencia:ExperienciaViewModel=viewModel(factory=fabrica());val ajustes by experiencia.estado.collectAsStateWithLifecycle()
 Column(Modifier.fillMaxSize().background(DegradadoMarca).statusBarsPadding().imePadding().verticalScroll(rememberScrollState())) {
  Column(Modifier.padding(horizontal=26.dp,vertical=28.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
   Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=Alignment.CenterVertically){LogoFoundia(true);IconButton(onClick={experiencia.tema(if(ajustes.tema=="oscuro")"claro" else "oscuro")}){Icon(Simbolos.Luna,"Cambiar tema",tint=Color.White)}};Spacer(Modifier.height(6.dp))
   Row(verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text("¡Qué bueno\nverte de nuevo!",style=MaterialTheme.typography.headlineLarge,color=Color.White);Spacer(Modifier.height(10.dp));Text("Tu comunidad te ayuda a encontrar.",color=Color(0xFFD0E1FF),style=MaterialTheme.typography.bodyMedium)};IlustracionEncuentro(2,Modifier.width(105.dp))}
  }
  Surface(shape=RoundedCornerShape(topStart=34.dp,topEnd=34.dp),color=MaterialTheme.colorScheme.surface,modifier=Modifier.fillMaxWidth()) {
   Column(Modifier.padding(24.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
    Text("Inicia sesión",style=MaterialTheme.typography.headlineSmall)
    Text("Vuelve a conectar con lo que importa.",style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)
    sesion.aviso?.let {AvisoEstado(it)}
    Campo("Correo",f.correo,vm::correo,habilitado=!accion.ocupada);Campo("Contraseña",f.password,vm::password,secreto=true,habilitado=!accion.ocupada)
    TextButton(onClick=recuperar,enabled=!accion.ocupada,modifier=Modifier.align(Alignment.End)){Text("Recuperar contraseña")}
    EstadoAccion(accion);BotonPrincipal("Iniciar sesión",vm::entrar,!accion.ocupada)
    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){HorizontalDivider(Modifier.weight(1f));Text("O",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant);HorizontalDivider(Modifier.weight(1f))}
    TextButton(onClick=registro,enabled=!accion.ocupada,modifier=Modifier.fillMaxWidth()){Text("¿Aún no tienes cuenta? Regístrate")}
    TextButton(onClick={verificar(f.correo.trim())},enabled=!accion.ocupada,modifier=Modifier.fillMaxWidth()){Text("Verificar mi correo")}
   }
  }
 }
}
@Composable fun RegistroScreen(volver:()->Unit,verificar:(String)->Unit,vm:RegistroViewModel=viewModel(factory=fabrica())) {
 val estado by vm.estado.collectAsStateWithLifecycle();val f by vm.form.collectAsStateWithLifecycle();val accion by vm.accion.collectAsStateWithLifecycle();val evento by vm.evento.collectAsStateWithLifecycle()
 LaunchedEffect(evento){evento?.let {vm.consumirEvento();verificar(it)}}
 Pagina("Sé parte del encuentro",volver) {
  Text("Una comunidad. Muchas historias por reencontrar.",style=MaterialTheme.typography.bodyLarge,color=MaterialTheme.colorScheme.onSurfaceVariant)
  Consulta(estado,vm::recargarDocumentos){docs,_->
   var leer by remember {mutableStateOf<String?>(null)}
   Panel("01 · Así te conoceremos") {
    Campo("Nombre",f.nombre,{vm.editar("nombre",it)},habilitado=!accion.ocupada)
    Campo("Apellido",f.apellido,{vm.editar("apellido",it)},habilitado=!accion.ocupada)
   }
   Panel("02 · Tu acceso seguro") {
    Campo("Correo",f.correo,{vm.editar("correo",it)},habilitado=!accion.ocupada)
    Campo("Contraseña",f.password,{vm.editar("password",it)},secreto=true,habilitado=!accion.ocupada)
    Text("Al menos 8 caracteres, mayúscula, minúscula, número y símbolo; hasta 72 bytes.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
    Campo("Confirmar contraseña",f.confirmar,{vm.editar("confirmar",it)},secreto=true,habilitado=!accion.ocupada)
   }
   Panel("03 · Tú decides") {
    AvisoEstado("Documentos del proyecto pendientes de revisión institucional. Versión ${docs.version}.")
    TextButton(onClick={leer=docs.terminos}){Text("Leer términos de uso")}
    TextButton(onClick={leer=docs.privacidad}){Text("Leer política de privacidad")}
    Row(Modifier.toggleable(value=f.acepta,role=Role.Checkbox,enabled=!accion.ocupada,onValueChange=vm::aceptar),verticalAlignment=Alignment.CenterVertically) {
     Checkbox(f.acepta,onCheckedChange=null,enabled=!accion.ocupada)
     Text("Acepto los términos y la política de privacidad de esta versión",Modifier.weight(1f),style=MaterialTheme.typography.bodySmall)
    }
   }
   EstadoAccion(accion);BotonPrincipal("Registrarme y recibir código",vm::registrar,f.acepta&&!accion.ocupada)
   TextButton(onClick=vm::recargarDocumentos,enabled=!accion.ocupada){Text("Actualizar documentos")}
   leer?.let {texto->AlertDialog(onDismissRequest={leer=null},title={Text("Documento presentado")},text={Column(Modifier.heightIn(max=420.dp).verticalScroll(rememberScrollState())){Text(texto)}},confirmButton={TextButton(onClick={leer=null}){Text("Cerrar documento")}})}
  }
 }
}
@Composable fun VerificarScreen(correoInicial:String,volver:()->Unit,vm:VerificacionViewModel=viewModel(factory=fabrica())) {
    val f by vm.form.collectAsStateWithLifecycle();val accion by vm.accion.collectAsStateWithLifecycle()
    LaunchedEffect(correoInicial){vm.iniciar(correoInicial)}
    Pagina("Verificar correo",volver) {
        Text("Ingresa el código recibido por correo. La cuenta no se activa hasta verificarlo.")
        Campo("Correo",f.correo,vm::correo);Campo("Código de 6 dígitos",f.codigo,vm::codigo)
        EstadoAccion(accion)
        Button(enabled=!accion.ocupada,onClick=vm::verificar) {Text("Verificar y entrar")}
        TextButton(enabled=!accion.ocupada && f.espera==0,onClick=vm::reenviar) {Text(if(f.espera>0) "Reenviar en ${f.espera} s" else "Reenviar código")}
    }
}
@Composable fun CuentaScreen(abrir:(String)->Unit,vm:PerfilViewModel=viewModel(factory=fabrica())) {
 val estado by vm.estado.collectAsStateWithLifecycle();val accion by vm.accion.collectAsStateWithLifecycle()
 LaunchedEffect(Unit){vm.recargar()}
 Pagina("Mi perfil") {
  Consulta(estado,vm::recargar) {usuario,_->
   Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(DegradadoMarca)) {
    Column(Modifier.padding(24.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
     Surface(shape=CircleShape,color=Color(0xFFDBE8FF),modifier=Modifier.size(64.dp)){Box(contentAlignment=Alignment.Center){Text(usuario.nombre.take(1)+usuario.apellido.take(1),style=MaterialTheme.typography.headlineSmall,color=AzulNoche)}}
     Text("${usuario.nombre} ${usuario.apellido}",style=MaterialTheme.typography.headlineSmall,color=Color.White)
     Text(usuario.correo,style=MaterialTheme.typography.bodyMedium,color=Color(0xFFD0E1FF));Text("COMUNIDAD TECSUP",style=MaterialTheme.typography.labelSmall,color=Cielo)
    }
   }
   Panel("Tu actividad") {
    FilaCuenta("Mis publicaciones","Lo que perdiste y encontraste",Simbolos.Caja){abrir("propios")}
    HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant)
    FilaCuenta("Notificaciones","Novedades de tus coincidencias",Simbolos.Avisos){abrir("avisos")}
   }
   Panel("Tu cuenta") {
    FilaCuenta("Editar perfil","Nombre y apellidos",Simbolos.Perfil){abrir("editar-perfil")}
    FilaCuenta("Seguridad","Cambiar tu contraseña",Simbolos.Candado){abrir("seguridad")}
    FilaCuenta("Preferencias de avisos","Elige qué novedades recibir",Simbolos.Ajustes){abrir("preferencias")}
    FilaCuenta("Privacidad","Términos y uso de tus datos",Simbolos.Escudo){abrir("privacidad")}
    FilaCuenta("Apariencia","Claro, oscuro o según tu dispositivo",Simbolos.Luna){abrir("apariencia")}
   }
   Panel{FilaCuenta("Solicitar eliminación","Revisa el estado de tu solicitud",Simbolos.Escudo){abrir("eliminacion")}}
  }
  EstadoAccion(accion)
  OutlinedButton(onClick=vm::salir,enabled=!accion.ocupada,modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)){Icon(Simbolos.Salir,null,Modifier.size(18.dp));Spacer(Modifier.width(10.dp));Text("Cerrar sesión")}
 }
}
@Composable private fun FilaCuenta(titulo:String,detalle:String,icono:ImageVector,accion:()->Unit) {
 Surface(onClick=accion,color=Color.Transparent,shape=RoundedCornerShape(12.dp)) {
  Row(Modifier.fillMaxWidth().padding(vertical=9.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
   Surface(color=MaterialTheme.colorScheme.primaryContainer,shape=RoundedCornerShape(12.dp)){Icon(icono,null,Modifier.padding(10.dp).size(20.dp),tint=MaterialTheme.colorScheme.primary)}
   Column(Modifier.weight(1f)){Text(titulo,style=MaterialTheme.typography.titleSmall);Text(detalle,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
   Icon(Simbolos.Flecha,null,Modifier.size(17.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant)
  }
 }
}
