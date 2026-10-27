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
import com.proyectointegrador.movil.network.Sesion
import kotlinx.coroutines.*
import retrofit2.HttpException
import com.google.gson.JsonParser

fun Throwable.mensajeUsuario(): String = when(this) {
    is HttpException -> {
        val texto=runCatching { JsonParser.parseString(response()?.errorBody()?.string()).asJsonObject.get("mensaje")?.asString }.getOrNull()
        when(code()) {
            401 -> texto ?: "Tu sesión venció. Vuelve a iniciar sesión."
            403 -> texto ?: "Tu cuenta no tiene permiso para esta acción."
            404 -> "El recurso no está disponible o fue retirado."
            423 -> texto ?: "La cuenta está bloqueada temporalmente. Intenta más tarde."
            429 -> texto ?: "Espera antes de solicitar otro envío."
            else -> texto ?: "No se pudo completar la operación. Reintenta."
        }
    }
    is java.io.IOException -> "No hay conexión con FoundIA. Revisa tu conexión y reintenta."
    else -> message ?: "No se pudo completar la operación."
}

class Accion(private val scope: CoroutineScope, private val token: String?) {
    var ocupada by mutableStateOf(false); private set
    var error by mutableStateOf<String?>(null)
    var mensaje by mutableStateOf<String?>(null)
    fun ejecutar(bloque: suspend () -> Unit) {
        if(ocupada) return
        ocupada=true;error=null;mensaje=null
        scope.launch {
            try { if(Sesion.actual == token) bloque() }
            catch(e: CancellationException) { throw e }
            catch(e: Exception) { if(Sesion.actual == token) error=e.mensajeUsuario() }
            finally { ocupada=false }
        }
    }
}
@Composable fun recordarAccion(): Accion {
    val scope=rememberCoroutineScope(); val token by Sesion.token.collectAsState()
    return remember(scope,token) { Accion(scope,token) }
}
@Composable fun EstadoAccion(accion: Accion) {
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
@Composable fun <T> Consulta(clave: Any?, cargar: suspend () -> T, contenido: @Composable (T,()->Unit) -> Unit) {
    var revision by remember(clave) { mutableIntStateOf(0) }
    var datos by remember(clave) { mutableStateOf<T?>(null) }
    var error by remember(clave) { mutableStateOf<String?>(null) }
    var cargando by remember(clave) { mutableStateOf(true) }
    val operacion by rememberUpdatedState(cargar)
    LaunchedEffect(clave,revision) {
        cargando=true;error=null;datos=null
        try { datos=operacion() }
        catch(e: CancellationException) {throw e}
        catch(e: Exception) {error=e.mensajeUsuario()}
        finally {cargando=false}
    }
    when {
        cargando -> { CircularProgressIndicator();Text("Cargando…") }
        error != null -> { Text(error!!,color=MaterialTheme.colorScheme.error);Button(onClick={revision++}) {Text("Reintentar")} }
        datos != null -> contenido(datos!!) {revision++}
    }
}
@Composable fun Confirmacion(titulo:String,texto:String,accion:Accion,cancelar:()->Unit,confirmar:()->Unit) {
    AlertDialog(onDismissRequest={if(!accion.ocupada) cancelar()},title={Text(titulo)},text={Column {
        Text(texto);EstadoAccion(accion)
    }},confirmButton={Button(onClick=confirmar,enabled=!accion.ocupada){Text(if(accion.ocupada) "Enviando…" else "Confirmar")}},
        dismissButton={TextButton(onClick=cancelar,enabled=!accion.ocupada){Text("Cancelar")}})
}
