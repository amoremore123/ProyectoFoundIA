package com.proyectointegrador.movil.ui.sprint

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation

@Composable fun EstadoAccion(accion: com.proyectointegrador.movil.ui.viewmodel.EstadoAccion) {
    if(accion.ocupada) LinearProgressIndicator(Modifier.fillMaxWidth())
    accion.error?.let { Text(it, color=MaterialTheme.colorScheme.error, modifier=Modifier.semantics { liveRegion=LiveRegionMode.Polite }) }
    accion.mensaje?.let { Text(it,modifier=Modifier.semantics { liveRegion=LiveRegionMode.Polite }) }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable fun Pagina(titulo: String, volver: (() -> Unit)? = null, contenido: @Composable ColumnScope.() -> Unit) {
    Scaffold(topBar={TopAppBar(title={Text(titulo)}, navigationIcon={if(volver != null) TextButton(onClick=volver) { Text("Volver") }})}) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement=Arrangement.spacedBy(12.dp),content=contenido)
    }
}
@Composable fun Campo(etiqueta: String, valor: String, cambiar: (String)->Unit, secreto: Boolean=false, habilitado: Boolean=true) {
    OutlinedTextField(value=valor,onValueChange=cambiar,label={Text(etiqueta)},singleLine=true,
        enabled=habilitado,modifier=Modifier.fillMaxWidth(),
        visualTransformation=if(secreto) PasswordVisualTransformation() else VisualTransformation.None)
}
@Composable fun <T> Consulta(estado: com.proyectointegrador.movil.ui.viewmodel.EstadoConsulta<T>, reintentar:()->Unit, contenido:@Composable (T,()->Unit)->Unit) {
    when {
        estado.cargando -> {CircularProgressIndicator();Text("Cargando…")}
        estado.error!=null -> {Text(estado.error,color=MaterialTheme.colorScheme.error);Button(onClick=reintentar){Text("Reintentar")}}
        estado.datos!=null -> contenido(estado.datos,reintentar)
    }
}
@Composable fun Confirmacion(titulo:String,texto:String,accion:com.proyectointegrador.movil.ui.viewmodel.EstadoAccion,cancelar:()->Unit,confirmar:()->Unit) {
    AlertDialog(onDismissRequest={if(!accion.ocupada) cancelar()},title={Text(titulo)},text={Column {
        Text(texto);EstadoAccion(accion)
    }},confirmButton={Button(onClick=confirmar,enabled=!accion.ocupada){Text(if(accion.ocupada) "Enviando…" else "Confirmar")}},
        dismissButton={TextButton(onClick=cancelar,enabled=!accion.ocupada){Text("Cancelar")}})
}
