package com.proyectointegrador.movil.ui.sprint

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.*
import com.proyectointegrador.movil.ui.components.*

@Composable fun EstadoAccion(accion:com.proyectointegrador.movil.ui.viewmodel.EstadoAccion) {
 if(accion.ocupada)LinearProgressIndicator(Modifier.fillMaxWidth())
 accion.error?.let {Box(Modifier.semantics {liveRegion=LiveRegionMode.Polite}){AvisoEstado(it,true)}}
 accion.mensaje?.let {Box(Modifier.semantics {liveRegion=LiveRegionMode.Polite}){AvisoEstado(it)}}
}
@Composable fun Pagina(titulo:String,volver:(()->Unit)?=null,contenido:@Composable ColumnScope.()->Unit) {
 Surface(color=MaterialTheme.colorScheme.background,modifier=Modifier.fillMaxSize()) {
  Column(Modifier.fillMaxSize().statusBarsPadding().imePadding().verticalScroll(rememberScrollState()).padding(horizontal=20.dp).padding(bottom=28.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
   Cabecera(titulo,volver=volver);contenido()
  }
 }
}
@Composable fun Campo(etiqueta:String,valor:String,cambiar:(String)->Unit,secreto:Boolean=false,habilitado:Boolean=true) {
 var visible by rememberSaveable {mutableStateOf(false)}
 val correo=etiqueta.contains("Correo",true)
 OutlinedTextField(value=valor,onValueChange=cambiar,label={Text(etiqueta)},singleLine=true,enabled=habilitado,modifier=Modifier.fillMaxWidth(),shape=MaterialTheme.shapes.medium,
  leadingIcon={Icon(if(secreto)Simbolos.Candado else if(correo)Simbolos.Correo else Simbolos.Perfil,null,Modifier.size(20.dp))},
  trailingIcon=if(secreto) {{IconButton(onClick={visible=!visible}){Icon(Simbolos.Ojo,if(visible)"Ocultar contraseña" else "Mostrar contraseña",Modifier.size(20.dp))}}} else null,
  keyboardOptions=KeyboardOptions(keyboardType=if(secreto)KeyboardType.Password else if(correo)KeyboardType.Email else KeyboardType.Text),
  visualTransformation=if(secreto&&!visible)PasswordVisualTransformation() else VisualTransformation.None)
}
@Composable fun <T> Consulta(estado:com.proyectointegrador.movil.ui.viewmodel.EstadoConsulta<T>,reintentar:()->Unit,contenido:@Composable (T,()->Unit)->Unit) {
 when {
  estado.cargando->Esqueleto()
  estado.error!=null->{AvisoEstado(estado.error,true);OutlinedButton(onClick=reintentar){Text("Reintentar")}}
  estado.datos!=null->contenido(estado.datos,reintentar)
 }
}
@Composable fun Confirmacion(titulo:String,texto:String,accion:com.proyectointegrador.movil.ui.viewmodel.EstadoAccion,cancelar:()->Unit,confirmar:()->Unit) {
 AlertDialog(onDismissRequest={if(!accion.ocupada)cancelar()},icon={Icon(Simbolos.Escudo,null)},title={Text(titulo)},text={Column(verticalArrangement=Arrangement.spacedBy(12.dp)){Text(texto);EstadoAccion(accion)}},
 confirmButton={Button(onClick=confirmar,enabled=!accion.ocupada){Text(if(accion.ocupada)"Enviando…" else "Confirmar")}},dismissButton={TextButton(onClick=cancelar,enabled=!accion.ocupada){Text("Cancelar")}})
}
