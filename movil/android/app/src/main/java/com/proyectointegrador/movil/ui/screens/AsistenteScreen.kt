package com.proyectointegrador.movil.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.net.Uri
import android.content.Intent
import android.speech.RecognizerIntent
import android.speech.RecognitionListener
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.core.content.ContextCompat
import java.util.Locale
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
import com.proyectointegrador.movil.model.PropuestaFindi
import com.proyectointegrador.movil.model.TarjetaFindi
import com.proyectointegrador.movil.ui.components.*
import com.proyectointegrador.movil.ui.viewmodel.*

@Composable fun AsistenteScreen(volver: () -> Unit, abrir: (String) -> Unit, autenticado: Boolean,
    vm: AsistenteViewModel = viewModel(factory = fabrica())) {
    val estado by vm.estado.collectAsStateWithLifecycle()
    var texto by rememberSaveable { mutableStateOf("") }
    var accesos by rememberSaveable { mutableStateOf(true) }
    val lista = rememberLazyListState()
    val contexto = LocalContext.current
    var mostrarPrivacidad by rememberSaveable { mutableStateOf(false) }
    var escuchando by remember { mutableStateOf(false) }
    var avisoVoz by remember { mutableStateOf<String?>(null) }
    var reconocedor by remember { mutableStateOf<SpeechRecognizer?>(null) }
    var sintetizador by remember { mutableStateOf<TextToSpeech?>(null) }
    var vozLista by remember { mutableStateOf(false) }
    DisposableEffect(contexto) {
        val motor = TextToSpeech(contexto.applicationContext) { resultado ->
            vozLista = resultado == TextToSpeech.SUCCESS
        }
        sintetizador = motor
        onDispose {
            reconocedor?.destroy()
            motor.stop()
            motor.shutdown()
        }
    }
    fun dictar() {
        avisoVoz = null
        if (!SpeechRecognizer.isRecognitionAvailable(contexto)) {
            avisoVoz = "El dictado no está disponible. Puedes escribir tu mensaje."
            return
        }
        try {
            val servicio = reconocedor ?: SpeechRecognizer.createSpeechRecognizer(contexto).also {
                it.setRecognitionListener(object : RecognitionListener {
                    override fun onReadyForSpeech(params: Bundle?) = Unit
                    override fun onBeginningOfSpeech() = Unit
                    override fun onRmsChanged(rmsdB: Float) = Unit
                    override fun onBufferReceived(buffer: ByteArray?) = Unit
                    override fun onEndOfSpeech() = Unit
                    override fun onPartialResults(partialResults: Bundle?) = Unit
                    override fun onEvent(eventType: Int, params: Bundle?) = Unit
                    override fun onError(error: Int) {
                        escuchando = false
                        avisoVoz = "No pude reconocer la voz. Prueba en un lugar tranquilo o escribe tu mensaje."
                    }
                    override fun onResults(results: Bundle?) {
                        escuchando = false
                        val resultado = results?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                            ?.firstOrNull()?.trim().orEmpty()
                        if (resultado.isNotEmpty()) texto = resultado.take(300)
                        else avisoVoz = "No se reconocieron palabras. Puedes escribir tu mensaje."
                    }
                })
                reconocedor = it
            }
            sintetizador?.stop()
            escuchando = true
            servicio.startListening(Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, "es-PE")
            })
        } catch (_: RuntimeException) {
            escuchando = false
            avisoVoz = "El dictado no está disponible. Puedes escribir tu mensaje."
        }
    }
    val permiso = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { concedido ->
        if (concedido) dictar() else avisoVoz = "Sin permiso de micrófono. Puedes escribir tu mensaje."
    }
    if (mostrarPrivacidad) {
        AlertDialog(
            onDismissRequest = { mostrarPrivacidad = false },
            title = { Text("Dictar un mensaje") },
            text = { Text("El proveedor de voz de tu dispositivo procesa el audio. Revisa el texto antes de enviarlo. También puedes escribir.") },
            confirmButton = { TextButton(onClick = {
                mostrarPrivacidad = false
                if (ContextCompat.checkSelfPermission(contexto, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) dictar()
                else permiso.launch(Manifest.permission.RECORD_AUDIO)
            }) { Text("Continuar") } },
            dismissButton = { TextButton(onClick = { mostrarPrivacidad = false }) { Text("Cancelar") } }
        )
    }
    fun leer(respuesta: String) {
        val motor = sintetizador ?: return
        if (!vozLista || motor.setLanguage(Locale.forLanguageTag("es-PE")) < 0 ||
            motor.speak(respuesta, TextToSpeech.QUEUE_FLUSH, null, "findi") == TextToSpeech.ERROR) {
            avisoVoz = "La lectura en voz alta no está disponible en este dispositivo."
        } else avisoVoz = null
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
            item(key = "bienvenida") { BurbujaFindi(estado.mensajes.first(), vm, ::navegar, ::leer) }
            if (accesos) item(key = "accesos") {
                Column(Modifier.padding(start = 40.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    AccesoChat("Buscar un objeto", Simbolos.Buscar) { abrir("buscar") }
                    AccesoChat("Reportar un objeto", Simbolos.Caja) { abrir("publicar?tipo=PERDIDO") }
                    AccesoChat("Consultar mis reportes", Simbolos.Ajustes) { navegar("propios") }
                    AccesoChat("¿Cómo funciona FoundIA?", Simbolos.Escudo) { vm.comoFunciona() }
                }
            }
            items(estado.mensajes.drop(1), key = { it.id }) { BurbujaFindi(it, vm, ::navegar, ::leer) }
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
        avisoVoz?.let { Text(it, Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.surface).padding(12.dp),
            color = MaterialTheme.colorScheme.onSurface, style = MaterialTheme.typography.bodySmall) }
        Surface(color = MaterialTheme.colorScheme.surface, shadowElevation = 8.dp) {
            Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 8.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                IconButton(onClick = { accesos = !accesos }) { Icon(Simbolos.Publicar, if (accesos) "Ocultar accesos rápidos" else "Mostrar accesos rápidos", tint = AzulFindi) }
                OutlinedTextField(value = texto, onValueChange = { if (it.length <= 300) texto = it },
                    placeholder = { Text("Escribe un mensaje…", style = MaterialTheme.typography.bodySmall) },
                    modifier = Modifier.weight(1f), shape = RoundedCornerShape(28.dp), maxLines = 4,
                    trailingIcon = {
                        IconButton(onClick = {
                            if (escuchando) {
                                reconocedor?.cancel()
                                escuchando = false
                            } else mostrarPrivacidad = true
                        }) {
                            Icon(Simbolos.Microfono, if (escuchando) "Cancelar dictado" else "Dictar mensaje",
                                Modifier.size(20.dp), tint = AzulFindi)
                        }
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

@Composable private fun BurbujaFindi(m: MensajeFindi, vm: AsistenteViewModel, abrir: (String) -> Unit, leer: (String) -> Unit) {
    var pendiente by remember(m.id) { mutableStateOf<PropuestaFindi?>(null) }
    pendiente?.let { propuesta ->
        AlertDialog(
            onDismissRequest = { pendiente = null },
            title = { Text("Confirmar acción") },
            text = { Text(propuesta.confirmacion.orEmpty()) },
            confirmButton = { TextButton(onClick = {
                pendiente = null
                rutaPropuesta(propuesta)?.let(abrir)
            }) { Text("Continuar") } },
            dismissButton = { TextButton(onClick = { pendiente = null }) { Text("Cancelar") } }
        )
    }
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (m.propio) Arrangement.End else Arrangement.Start) {
        if (!m.propio) { AvatarFindi(Modifier.size(30.dp)); Spacer(Modifier.width(10.dp)) }
        Surface(shape = if (m.propio) RoundedCornerShape(20.dp, 20.dp, 5.dp, 20.dp) else RoundedCornerShape(5.dp, 20.dp, 20.dp, 20.dp),
            color = if (m.propio) AzulFindi else MaterialTheme.colorScheme.surface,
            contentColor = if (m.propio) Color.White else MaterialTheme.colorScheme.onSurface,
            modifier = Modifier.widthIn(max = 320.dp).weight(1f, fill = false)) {
            Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(m.texto, style = MaterialTheme.typography.bodyMedium)
                if (!m.propio) TextButton(onClick = { leer(m.texto) }) { Text("Leer en voz alta") }
                m.respuesta?.objetos?.forEach { o ->
                    Surface(onClick = { abrir("detalle/${o.id}") }, shape = RoundedCornerShape(14.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                        Column(Modifier.fillMaxWidth().padding(12.dp)) {
                            Text(o.nombre, style = MaterialTheme.typography.titleSmall)
                            o.ubicacion?.let { Text(it, style = MaterialTheme.typography.bodySmall) }
                            Text("Ver publicación →", style = MaterialTheme.typography.labelSmall)
                        }
                    }
                }
                m.respuesta?.tarjetas.orEmpty().forEach { TarjetaDeFindi(it, abrir) }
                val propuestas = m.respuesta?.propuestas.orEmpty().filter { rutaPropuesta(it) != null }
                propuestas.forEach { propuesta ->
                    OutlinedButton(onClick = { pendiente = propuesta }) { Text(propuesta.texto) }
                }
                if (propuestas.isEmpty()) m.respuesta?.accion?.let { a -> TextButton(onClick = { abrir(a.ruta) }) { Text(a.texto) } }
                m.respuesta?.fuente?.let { fuente -> Text(if (fuente in listOf("ia", "claude")) "Interpretado con IA" else "Interpretación local", style = MaterialTheme.typography.labelSmall) }
                m.reintentar?.let { texto -> TextButton(onClick = { vm.enviar(texto, repetir = true) }, enabled = !vm.estado.value.pensando) { Text("Reintentar") } }
            }
        }
    }
}

private fun rutaPropuesta(propuesta: PropuestaFindi): String? {
    if (!propuesta.requiereConfirmacion || propuesta.confirmacion.isNullOrBlank()) return null
    val datos = propuesta.datos
    return when (propuesta.tipo) {
        "PRELLENAR_PUBLICAR" -> if (propuesta.destino == "/publicar") {
            val tipo = datos?.tipo?.takeIf { it in listOf("PERDIDO", "ENCONTRADO") } ?: "PERDIDO"
            val categoria = datos?.categoriaId?.takeIf { it > 0 } ?: 0
            val ubicacion = Uri.encode(datos?.ubicacion.orEmpty().take(255))
            "publicar?tipo=$tipo&categoria=$categoria&ubicacion=$ubicacion"
        } else null
        "ABRIR_RECLAMO" -> datos?.objetoId?.takeIf { it > 0 && propuesta.destino == "/objeto/$it" }
            ?.let { "reclamo-custodia/$it" }
        else -> null
    }
}

@Composable
private fun TarjetaDeFindi(tarjeta: TarjetaFindi, abrir: (String) -> Unit) {
    val datos = tarjeta.datos ?: return
    val ruta: String
    val detalle: String
    when (tarjeta.tipo) {
        "OBJETO" -> {
            val id = datos.id?.takeIf { it > 0 } ?: return
            if (tarjeta.enlace != "/objeto/$id") return
            ruta = "detalle/$id"
            detalle = listOfNotNull(datos.tipo, datos.estado?.let { com.proyectointegrador.movil.domain.etiquetaEstadoObjeto(it) }, datos.ubicacion, datos.categoria).joinToString(" · ")
        }
        "RECLAMOS", "QR_RETIRO" -> {
            if (tarjeta.enlace != "/mis-publicaciones") return
            ruta = "propios"
            detalle = if (tarjeta.tipo == "QR_RETIRO") "QR vigentes: " + (datos.vigentes ?: 0)
                else listOf("Pendientes" to datos.pendientes, "Aceptados" to datos.aceptados,
                    "Retirados" to datos.retirados, "Rechazados" to datos.rechazados)
                    .joinToString(" · ") { (etiqueta, valor) -> etiqueta + ": " + (valor ?: 0) }
        }
        else -> return
    }
    OutlinedCard(onClick = { abrir(ruta) }, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(12.dp)) {
            Text(tarjeta.titulo, style = MaterialTheme.typography.titleSmall)
            Text(detalle, style = MaterialTheme.typography.bodySmall)
        }
    }
}
