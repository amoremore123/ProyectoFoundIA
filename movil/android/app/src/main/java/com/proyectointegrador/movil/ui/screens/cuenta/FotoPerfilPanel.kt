package com.proyectointegrador.movil.ui.screens.cuenta

import android.graphics.BitmapFactory
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.proyectointegrador.movil.di.fabrica
import com.proyectointegrador.movil.ui.components.*
import com.proyectointegrador.movil.ui.viewmodel.FotoPerfilViewModel

/** H64: foto de perfil. Solo la ve la propia persona y quien conversa con ella; no aparece en publicaciones. */
@Composable
fun FotoPerfilPanel(vm: FotoPerfilViewModel = viewModel(factory = fabrica())) {
    val foto by vm.foto.collectAsStateWithLifecycle()
    val accion by vm.accion.collectAsStateWithLifecycle()
    val elegir = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri -> uri?.let { vm.cambiar(it.toString()) } }
    val imagen = remember(foto.bytes) { foto.bytes?.let { runCatching { BitmapFactory.decodeByteArray(it, 0, it.size) }.getOrNull() } }

    Panel("Tu foto de perfil") {
        Row(horizontalArrangement = Arrangement.spacedBy(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.size(84.dp).clip(CircleShape)) {
                if (imagen != null) {
                    Image(imagen.asImageBitmap(), "Tu foto de perfil", Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                } else {
                    Box(contentAlignment = Alignment.Center) {
                        if (foto.cargando) CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
                        else Icon(Simbolos.Perfil, "Sin foto de perfil", Modifier.size(36.dp))
                    }
                }
            }
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                OutlinedButton(onClick = { elegir.launch("image/*") }, enabled = !accion.ocupada) {
                    Text(if (imagen == null) "Elegir foto" else "Cambiar foto")
                }
                if (imagen != null) TextButton(onClick = vm::quitar, enabled = !accion.ocupada) { Text("Quitar foto") }
            }
        }
        Text("Solo la ves tú y quien converse contigo. No se muestra en las publicaciones.", style = MaterialTheme.typography.bodySmall)
        foto.error?.let { AvisoEstado(it, true) }
        EstadoAccion(accion)
    }
}
