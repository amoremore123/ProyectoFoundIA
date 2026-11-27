package com.proyectointegrador.movil.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.proyectointegrador.movil.data.ubicacion.ResultadoUbicacion
import com.proyectointegrador.movil.data.ubicacion.UbicacionActual
import com.proyectointegrador.movil.domain.LUGARES_CAMPUS
import com.proyectointegrador.movil.ui.components.AvisoEstado
import com.proyectointegrador.movil.ui.components.MapaUbicacion
import com.proyectointegrador.movil.ui.viewmodel.FormPublicacion
import com.proyectointegrador.movil.ui.viewmodel.PublicarViewModel
import java.util.Locale
import kotlinx.coroutines.launch

/** Paso «Ubicación»: lugar del campus, GPS o texto libre. Si se niega el permiso siempre se puede escribir. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun PasoUbicacion(f: FormPublicacion, viewModel: PublicarViewModel, ocupada: Boolean) {
    val contexto = LocalContext.current
    val alcance = rememberCoroutineScope()
    var buscando by remember { mutableStateOf(false) }
    var aviso by remember { mutableStateOf<String?>(null) }
    val bloqueado = ocupada || f.publicadoId != null

    fun buscar() {
        buscando = true
        aviso = null
        alcance.launch {
            when (val r = UbicacionActual.obtener(contexto)) {
                is ResultadoUbicacion.Exito -> viewModel.ubicacionGps(r.coordenadas, UbicacionActual.direccionDe(contexto, r.coordenadas))
                ResultadoUbicacion.Apagada -> aviso = "Activa la ubicación del teléfono (GPS) o elige un lugar del campus."
                ResultadoUbicacion.SinSenal -> aviso = "No se pudo obtener tu ubicación. Acércate a una ventana, reintenta o escribe el lugar."
            }
            buscando = false
        }
    }

    val permiso = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { concedidos ->
        if (concedidos.values.any { it }) buscar()
        else aviso = "Sin permiso de ubicación. Puedes elegir un lugar del campus o escribirlo."
    }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("¿Dónde ocurrió?", style = MaterialTheme.typography.headlineSmall)
        Text("Elige un lugar del campus, usa tu ubicación o escribe el pabellón, aula o punto de referencia.", color = MaterialTheme.colorScheme.onSurfaceVariant)
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            LUGARES_CAMPUS.forEach { lugar ->
                FilterChip(selected = f.ubicacion == lugar && f.latitud == null, onClick = { viewModel.lugarCampus(lugar) }, enabled = !bloqueado, label = { Text(lugar) })
            }
        }
        OutlinedButton(
            onClick = {
                if (UbicacionActual.tienePermiso(contexto)) buscar()
                else permiso.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            },
            enabled = !bloqueado && !buscando,
            modifier = Modifier.fillMaxWidth(),
        ) { Text(if (buscando) "Buscando tu ubicación…" else "Usar mi ubicación") }
        aviso?.let { AvisoEstado(it, true) }
        OutlinedTextField(
            value = f.ubicacion,
            onValueChange = { viewModel.editar("ubicacion", it) },
            label = { Text("Ubicación") },
            modifier = Modifier.fillMaxWidth(),
            isError = f.errores["ubicacion"] != null,
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
            supportingText = f.errores["ubicacion"]?.let { { Text(it) } },
        )
        if (f.latitud != null && f.longitud != null) {
            Text(String.format(Locale.ROOT, "Coordenadas guardadas: %.5f, %.5f", f.latitud, f.longitud), style = MaterialTheme.typography.bodySmall)
            MapaUbicacion(f.latitud, f.longitud, f.ubicacion.ifBlank { "Ubicación del objeto" })
            TextButton(onClick = viewModel::quitarGps, enabled = !bloqueado) { Text("Quitar las coordenadas") }
        }
    }
}
