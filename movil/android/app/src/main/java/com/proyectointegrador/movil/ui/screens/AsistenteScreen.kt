package com.proyectointegrador.movil.ui.screens

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.proyectointegrador.movil.di.fabrica
import com.proyectointegrador.movil.ui.components.*
import com.proyectointegrador.movil.ui.viewmodel.*

@Composable fun AsistenteScreen(volver: () -> Unit, abrir: (String) -> Unit, autenticado: Boolean,
    vm: AsistenteViewModel = viewModel(factory = fabrica())) {
    val estado by vm.estado.collectAsStateWithLifecycle()
    var texto by rememberSaveable { mutableStateOf("") }
    var accesos by rememberSaveable { mutableStateOf(true) }
    val lista = rememberLazyListState()
    val contexto = LocalContext.current
    val voz = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { resultado ->
        if (resultado.resultCode == Activity.RESULT_OK) {
            resultado.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()?.let { texto = it.take(300) }
        }
    }
    fun navegar(ruta: String) = abrir(if (ruta == "propios" && !autenticado) "perfil" else ruta)
    fun enviar() { if (texto.isNotBlank() && !estado.pensando) { vm.enviar(texto); texto = "" } }
    LaunchedEffect(estado.mensajes.size, estado.pensando, accesos) {
        lista.animateScrollToItem((estado.mensajes.size + (if (accesos) 1 else 0) + (if (estado.pensando) 1 else 0) - 1).coerceAtLeast(0))
    }
    Column(Modifier.fillMaxSize().background(MarinoFindi).statusBarsPadding().imePadding()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = volver) { Icon(Simbolos.Volver, "Volver", tint = Color.White) }
            AvatarFindi(Modifier.size(40.dp))
            Column(Modifier.padding(start = 10.dp).weight(1f)) {
                Text("FINDI", color = Color.White, style = MaterialTheme.typography.titleMedium)
                Text(if (estado.pensando) "Buscando…" else "Asistente de FoundIA", color = Color(0xFFC6DCFF), style = MaterialTheme.typography.labelSmall)
            }
        }
        LazyColumn(state = lista, modifier = Modifier.weight(1f).fillMaxWidth().background(Brush.verticalGradient(
            listOf(MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.background))),
            contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(16.dp)) {
            item(key = "bienvenida") { BurbujaFindi(estado.mensajes.first(), vm, ::navegar) }
            if (accesos) item(key = "accesos") {
                Column(Modifier.padding(start = 40.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AccesoChat("Buscar un objeto", Simbolos.Buscar) { abrir("buscar") }
                    AccesoChat("Reportar un objeto", Simbolos.Caja) { abrir("publicar?tipo=PERDIDO") }
                    AccesoChat("Consultar mis reportes", Simbolos.Ajustes) { navegar("propios") }
                    AccesoChat("¿Cómo funciona FoundIA?", Simbolos.Escudo) { vm.comoFunciona() }
                }
            }
            items(estado.mensajes.drop(1), key = { it.id }) { BurbujaFindi(it, vm, ::navegar) }
            if (estado.pensando) item(key = "pensando") {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    AvatarFindi(Modifier.size(30.dp))
                    Surface(shape = RoundedCornerShape(18.dp)) {
                        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
                            CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                            Text("Buscando publicaciones…", style = MaterialTheme.typography.bodySmall)
                        }
                    }
                }
            }
        }
        Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 8.dp) {
            Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = { accesos = !accesos }) { Icon(Simbolos.Publicar, if (accesos) "Ocultar accesos rápidos" else "Mostrar accesos rápidos", tint = AzulFindi) }
                OutlinedTextField(value = texto, onValueChange = { if (it.length <= 300) texto = it },
                    placeholder = { Text("Escribe un mensaje…", style = MaterialTheme.typography.bodySmall) },
                    modifier = Modifier.weight(1f), shape = RoundedCornerShape(28.dp), maxLines = 4,
                    trailingIcon = {
                        IconButton(onClick = {
                            try { voz.launch(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-PE")
                                putExtra(RecognizerIntent.EXTRA_PROMPT, "Dicta tu mensaje y revísalo antes de enviarlo")
                            }) } catch (_: Exception) { Toast.makeText(contexto, "El dictado no está disponible. Puedes escribir tu mensaje.", Toast.LENGTH_LONG).show() }
                        }) { Icon(Simbolos.Microfono, "Dictar mensaje", Modifier.size(20.dp), tint = AzulFindi) }
                    })
                FilledIconButton(onClick = ::enviar, enabled = texto.isNotBlank() && !estado.pensando,
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = AzulFindi)) {
                    Icon(Simbolos.Flecha, "Enviar mensaje")
                }
            }
        }
    }
}

@Composable private fun AccesoChat(texto: String, icono: androidx.compose.ui.graphics.vector.ImageVector, accion: () -> Unit) {
    Surface(onClick = accion, shape = RoundedCornerShape(22.dp), color = MaterialTheme.colorScheme.surface,
        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outlineVariant), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Icon(icono, null, Modifier.size(20.dp), tint = AzulFindi)
            Text(texto, Modifier.weight(1f), style = MaterialTheme.typography.labelMedium)
            Icon(Simbolos.Flecha, null, Modifier.size(16.dp))
        }
    }
}

@Composable private fun BurbujaFindi(m: MensajeFindi, vm: AsistenteViewModel, abrir: (String) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (m.propio) Arrangement.End else Arrangement.Start) {
        if (!m.propio) { AvatarFindi(Modifier.size(30.dp)); Spacer(Modifier.width(10.dp)) }
        Surface(shape = if (m.propio) RoundedCornerShape(20.dp, 20.dp, 5.dp, 20.dp) else RoundedCornerShape(5.dp, 20.dp, 20.dp, 20.dp),
            color = if (m.propio) AzulFindi else MaterialTheme.colorScheme.surface,
            contentColor = if (m.propio) Color.White else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.widthIn(max = 320.dp).weight(1f, fill = false)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(m.texto, style = MaterialTheme.typography.bodyMedium)
                m.respuesta?.objetos?.forEach { o ->
                    Surface(onClick = { abrir("detalle/${o.id}") }, shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                        Column(Modifier.fillMaxWidth().padding(12.dp)) {
                            Text(o.nombre, style = MaterialTheme.typography.titleSmall)
                            o.ubicacion?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                            Text("Ver publicación →", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                m.respuesta?.accion?.let { a -> TextButton(onClick = { abrir(a.ruta) }) { Text(a.texto) } }
                m.respuesta?.fuente?.let { fuente -> Text(if (fuente in listOf("ia", "claude")) "Interpretado con IA" else "Interpretación local", style = MaterialTheme.typography.labelSmall) }
                m.reintentar?.let { texto -> TextButton(onClick = { vm.enviar(texto, repetir = true) }, enabled = !vm.estado.value.pensando) { Text("Reintentar") } }
            }
        }
    }
}
