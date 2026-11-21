package com.proyectointegrador.movil.ui.screens.chat

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.SubcomposeAsyncImage
import com.proyectointegrador.movil.di.fabrica
import com.proyectointegrador.movil.domain.MAXIMO_MENSAJE_CHAT
import com.proyectointegrador.movil.domain.textoSinLeer
import com.proyectointegrador.movil.domain.urlImagen
import com.proyectointegrador.movil.model.Conversacion
import com.proyectointegrador.movil.model.MensajeChat
import com.proyectointegrador.movil.ui.components.*
import com.proyectointegrador.movil.ui.viewmodel.ChatViewModel
import com.proyectointegrador.movil.ui.viewmodel.ConversacionesViewModel

/** H55: mis conversaciones. En la biblioteca, el personal ve aquí la bandeja de reclamos (H63). */
@Composable
fun ConversacionesScreen(volver: () -> Unit, abrir: (String) -> Unit, vm: ConversacionesViewModel = viewModel(factory = fabrica())) {
    val estado by vm.estado.collectAsStateWithLifecycle()
    Pagina("Mensajes", volver) {
        Consulta(estado, vm::recargar) { lectura, recargar ->
            val lista = lectura.datos
            lectura.aviso?.let { AvisoEstado(it) }
            if (lista.isEmpty()) {
                Panel { Text("Todavía no tienes conversaciones. Se abren desde una coincidencia con contacto o desde un reclamo en custodia.") }
            } else {
                lista.forEach { FilaConversacion(it) { abrir("conversacion/${it.id}") } }
            }
            TextButton(onClick = recargar) { Text("Actualizar") }
        }
    }
}

@Composable
private fun FilaConversacion(c: Conversacion, abrir: () -> Unit) {
    val sinLeer = textoSinLeer(c.sinLeer)
    Surface(
        onClick = abrir, shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp, modifier = Modifier.fillMaxWidth()
    ) {
        Row(Modifier.padding(14.dp), horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(c.otro?.nombre ?: "Conversación", style = MaterialTheme.typography.titleSmall)
                Text("Sobre: " + (c.objetoNombre ?: "una publicación"), style = MaterialTheme.typography.bodySmall)
                Text(
                    c.ultimoMensaje?.let { it.texto ?: "Foto" } ?: "Sin mensajes todavía",
                    style = MaterialTheme.typography.bodyMedium, maxLines = 1
                )
                if (c.soloLectura) Text("Solo lectura", style = MaterialTheme.typography.labelSmall)
            }
            if (sinLeer != null) Badge { Text(sinLeer, Modifier.semantics { contentDescription = "$sinLeer mensajes sin leer" }) }
        }
    }
}

@Composable
fun ChatScreen(id: Long, volver: () -> Unit, vm: ChatViewModel = viewModel(key = "chat-$id", factory = fabrica(objetoId = id))) {
    val chat by vm.chat.collectAsStateWithLifecycle()
    val accion by vm.accion.collectAsStateWithLifecycle()
    var texto by rememberSaveable { mutableStateOf("") }
    var reportando by remember { mutableStateOf<MensajeChat?>(null) }
    var motivo by remember { mutableStateOf("") }
    var confirmarBloqueo by remember { mutableStateOf(false) }
    val lista = rememberLazyListState()
    val elegirFoto = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let { vm.enviarFoto(it.toString()) } }

    LaunchedEffect(chat.mensajes.size, chat.pendientes.size) {
        val total = chat.mensajes.size + chat.pendientes.size
        if (total > 0) lista.animateScrollToItem(total - 1)
    }

    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding().imePadding().padding(horizontal = 16.dp)) {
            Cabecera("Conversación", volver = volver, accion = {
                if (!chat.soloLectura) TextButton(onClick = { if (chat.bloqueada) vm.bloquear(false) else confirmarBloqueo = true }) {
                    Text(if (chat.bloqueada) "Desbloquear" else "Bloquear")
                }
            })
            EstadoAccion(accion)
            if (chat.sinConexion) AvisoEstado("Sin conexión. Lo que escribas se enviará cuando vuelva la red.")
            chat.error?.let { Column { AvisoEstado(it, true); OutlinedButton(onClick = vm::reintentar) { Text("Reintentar") } } }
            when {
                chat.cargando -> Esqueleto()
                else -> LazyColumn(Modifier.weight(1f).fillMaxWidth(), state = lista, verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (chat.mensajes.isEmpty()) item { Text("Aún no hay mensajes. Escribe el primero.", Modifier.padding(vertical = 16.dp)) }
                    items(chat.mensajes, key = { it.id }) { m ->
                        Burbuja(m, propio = m.autorId == chat.miId, reportar = { reportando = m; motivo = "" })
                    }
                    items(chat.pendientes, key = { "pendiente-" + it.localId }) { p ->
                        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                            Column(Modifier.widthIn(max = 290.dp).clip(RoundedCornerShape(16.dp)).background(MaterialTheme.colorScheme.surfaceVariant).padding(10.dp)) {
                                Text(p.texto)
                                Text("Pendiente de enviar", style = MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                }
            }
            if (chat.soloLectura) {
                AvisoEstado("La conversación terminó y está en solo lectura.")
            } else if (chat.bloqueada) {
                AvisoEstado("Hay un bloqueo en esta conversación. No se pueden enviar mensajes.")
            } else {
                chat.aviso?.let { AvisoEstado(it, true) }
                Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.Bottom, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    IconButton(onClick = { elegirFoto.launch("image/*") }, enabled = !chat.enviando) {
                        Icon(Simbolos.Foto, "Adjuntar una foto")
                    }
                    OutlinedTextField(
                        value = texto, onValueChange = { texto = it.take(MAXIMO_MENSAJE_CHAT) }, modifier = Modifier.weight(1f),
                        label = { Text("Mensaje") }, maxLines = 4, enabled = !chat.enviando,
                        supportingText = { Text("${texto.length}/$MAXIMO_MENSAJE_CHAT") },
                        keyboardOptions = KeyboardOptions(imeAction = androidx.compose.ui.text.input.ImeAction.Default)
                    )
                    FilledIconButton(onClick = { vm.enviar(texto) { texto = "" } }, enabled = texto.isNotBlank() && !chat.enviando) {
                        Icon(Simbolos.Flecha, "Enviar mensaje")
                    }
                }
            }
        }
    }

    if (confirmarBloqueo) Confirmacion(
        "Bloquear conversación", "Ninguna de las dos personas podrá escribir hasta que la desbloquees.",
        accion, { confirmarBloqueo = false }, { confirmarBloqueo = false; vm.bloquear(true) }
    )
    reportando?.let { m ->
        AlertDialog(
            onDismissRequest = { reportando = null },
            title = { Text("Reportar mensaje") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Cuéntanos por qué. Un administrador lo revisará.")
                    OutlinedTextField(value = motivo, onValueChange = { motivo = it.take(255) }, label = { Text("Motivo") }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = { TextButton(onClick = { vm.reportar(m.id, motivo); reportando = null }, enabled = motivo.trim().length >= 3) { Text("Reportar") } },
            dismissButton = { TextButton(onClick = { reportando = null }) { Text("Cancelar") } }
        )
    }
}

@Composable
private fun Burbuja(m: MensajeChat, propio: Boolean, reportar: () -> Unit) {
    val fondo = if (propio) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    Row(Modifier.fillMaxWidth(), horizontalArrangement = if (propio) Arrangement.End else Arrangement.Start) {
        Column(
            Modifier.widthIn(max = 290.dp).clip(RoundedCornerShape(16.dp)).background(fondo).padding(10.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            if (!propio) Text(m.autorNombre ?: "Persona", style = MaterialTheme.typography.labelSmall)
            urlImagen(m.imagenUrl)?.let { url ->
                SubcomposeAsyncImage(
                    model = url, contentDescription = "Foto enviada en la conversación",
                    modifier = Modifier.fillMaxWidth().heightIn(max = 220.dp).clip(RoundedCornerShape(10.dp)), contentScale = ContentScale.Crop,
                    loading = { CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp) },
                    error = { Text("Foto no disponible", style = MaterialTheme.typography.bodySmall) }
                )
            }
            m.texto?.let { Text(it) }
            if (!propio) Text(
                "Reportar", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.error,
                modifier = Modifier.clickable(onClickLabel = "Reportar este mensaje", onClick = reportar)
            )
        }
    }
}

/** Abre la conversación de un contacto (H55) o de un reclamo con la biblioteca (H63), creándola si no existe. */
@Composable
fun BotonConversar(
    contactoId: Long? = null, reclamoId: Long? = null, texto: String = "Conversar",
    vm: com.proyectointegrador.movil.ui.viewmodel.IniciarChatViewModel =
        viewModel(key = "iniciar-chat-$contactoId-$reclamoId", factory = fabrica())
) {
    val accion by vm.accion.collectAsStateWithLifecycle()
    val evento by vm.evento.collectAsStateWithLifecycle()
    val abrir = LocalAbrirRuta.current
    LaunchedEffect(evento) { evento?.let { vm.consumirEvento(); abrir?.invoke(it) } }
    OutlinedButton(
        onClick = { if (contactoId != null) vm.porContacto(contactoId) else reclamoId?.let(vm::porReclamo) },
        enabled = !accion.ocupada && abrir != null, modifier = Modifier.fillMaxWidth()
    ) { Text(texto) }
    EstadoAccion(accion)
}
