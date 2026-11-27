package com.proyectointegrador.movil.ui.screens

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.proyectointegrador.movil.di.fabrica
import com.proyectointegrador.movil.domain.rutaInternaDeEnlace
import com.proyectointegrador.movil.ui.viewmodel.AvisosDestacadosViewModel

/** H71: avisos destacados vigentes arriba de Inicio. Si no hay o falla la consulta, no se muestra nada. */
@Composable
fun AvisosDestacadosInicio(abrir: (String) -> Unit, vm: AvisosDestacadosViewModel = viewModel(factory = fabrica())) {
    val estado by vm.estado.collectAsStateWithLifecycle()
    val avisos = estado.datos.orEmpty()
    if (avisos.isEmpty()) return
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        avisos.take(5).forEach { aviso ->
            val ruta = rutaInternaDeEnlace(aviso.enlace)
            Surface(
                shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.tertiaryContainer,
                modifier = Modifier.fillMaxWidth(),
                onClick = { if (ruta != null) abrir(ruta) }, enabled = ruta != null
            ) {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(aviso.titulo, style = MaterialTheme.typography.titleSmall)
                    Text(aviso.texto, style = MaterialTheme.typography.bodyMedium)
                    aviso.fechaFin?.let { Text("Hasta el $it", style = MaterialTheme.typography.labelSmall) }
                }
            }
        }
    }
}
