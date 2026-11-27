package com.proyectointegrador.movil.ui.screens

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
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
import com.proyectointegrador.movil.domain.FiltrosAvanzados
import com.proyectointegrador.movil.domain.RADIOS_KM
import com.proyectointegrador.movil.domain.RADIO_KM_POR_DEFECTO
import com.proyectointegrador.movil.domain.formatoRadio
import com.proyectointegrador.movil.ui.components.AvisoEstado
import com.proyectointegrador.movil.ui.components.Panel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import kotlinx.coroutines.launch

/**
 * Panel de la búsqueda avanzada: lugar, rango de fechas y cercanía. Es el mismo conjunto que la web.
 * Los filtros se aplican con «Aplicar» para no consultar en cada tecla.
 */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun FiltrosAvanzadosPanel(actuales: FiltrosAvanzados, error: String?, aplicar: (FiltrosAvanzados) -> Unit, cerrar: () -> Unit) {
    val contexto = LocalContext.current
    val alcance = rememberCoroutineScope()
    var borrador by remember { mutableStateOf(actuales) }
    var buscando by remember { mutableStateOf(false) }
    var aviso by remember { mutableStateOf<String?>(null) }
    var elegirFecha by remember { mutableStateOf<String?>(null) }

    fun cercaDeMi() {
        buscando = true
        aviso = null
        alcance.launch {
            when (val r = UbicacionActual.obtener(contexto)) {
                is ResultadoUbicacion.Exito -> borrador = borrador.copy(latitud = r.coordenadas.latitud, longitud = r.coordenadas.longitud, radioKm = borrador.radioKm ?: RADIO_KM_POR_DEFECTO)
                ResultadoUbicacion.Apagada -> aviso = "Activa la ubicación del teléfono (GPS) para buscar cerca de ti."
                ResultadoUbicacion.SinSenal -> aviso = "No se pudo obtener tu ubicación. Reintenta o busca por lugar."
            }
            buscando = false
        }
    }
    val permiso = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { concedidos ->
        if (concedidos.values.any { it }) cercaDeMi() else aviso = "Sin permiso de ubicación. Puedes buscar por lugar."
    }

    Panel("Más filtros") {
        Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(
                value = borrador.ubicacion, onValueChange = { borrador = borrador.copy(ubicacion = it.take(255)) },
                label = { Text("Lugar (por ejemplo, Biblioteca)") }, singleLine = true, modifier = Modifier.fillMaxWidth(),
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(onClick = { elegirFecha = "desde" }, modifier = Modifier.weight(1f)) { Text(if (borrador.desde.isBlank()) "Desde" else "Desde ${borrador.desde}") }
                OutlinedButton(onClick = { elegirFecha = "hasta" }, modifier = Modifier.weight(1f)) { Text(if (borrador.hasta.isBlank()) "Hasta" else "Hasta ${borrador.hasta}") }
            }
            OutlinedButton(
                onClick = {
                    if (UbicacionActual.tienePermiso(contexto)) cercaDeMi()
                    else permiso.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
                },
                enabled = !buscando, modifier = Modifier.fillMaxWidth(),
            ) { Text(if (buscando) "Buscando tu ubicación…" else if (borrador.latitud != null) "Cerca de mí: ubicación guardada" else "Buscar cerca de mí") }
            if (borrador.latitud != null) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    RADIOS_KM.forEach { km ->
                        FilterChip(selected = (borrador.radioKm ?: RADIO_KM_POR_DEFECTO) == km, onClick = { borrador = borrador.copy(radioKm = km) }, label = { Text(formatoRadio(km)) })
                    }
                }
                TextButton(onClick = { borrador = borrador.copy(latitud = null, longitud = null, radioKm = null) }) { Text("Quitar la cercanía") }
            }
            aviso?.let { AvisoEstado(it, true) }
            error?.let { AvisoEstado(it, true) }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                Button(onClick = { aplicar(borrador) }, modifier = Modifier.weight(1f)) { Text("Aplicar") }
                TextButton(onClick = { borrador = FiltrosAvanzados(); aplicar(FiltrosAvanzados()) }) { Text("Limpiar") }
                TextButton(onClick = cerrar) { Text("Cerrar") }
            }
        }
    }

    elegirFecha?.let { cual ->
        val estado = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { elegirFecha = null },
            confirmButton = {
                TextButton(onClick = {
                    estado.selectedDateMillis?.let { ms ->
                        val texto = SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(ms))
                        borrador = if (cual == "desde") borrador.copy(desde = texto) else borrador.copy(hasta = texto)
                    }
                    elegirFecha = null
                }) { Text("Confirmar fecha") }
            },
            dismissButton = {
                TextButton(onClick = {
                    borrador = if (cual == "desde") borrador.copy(desde = "") else borrador.copy(hasta = "")
                    elegirFecha = null
                }) { Text("Quitar") }
            },
        ) { DatePicker(estado, title = { Text(if (cual == "desde") "Desde" else "Hasta", Modifier.padding(20.dp), style = MaterialTheme.typography.titleMedium) }) }
    }
}
