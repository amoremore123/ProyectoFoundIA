package com.proyectointegrador.movil.ui.screens

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SelectableDates
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.proyectointegrador.movil.di.fabrica
import com.proyectointegrador.movil.domain.etiquetaEstadoObjeto
import com.proyectointegrador.movil.ui.components.*
import com.proyectointegrador.movil.ui.viewmodel.EditarPublicacionViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** Editar una publicación propia (H5). Mismos campos y reglas que la web; el tipo no se cambia. */
@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun EditarPublicacionScreen(
    objetoId: Long,
    volver: () -> Unit,
    vm: EditarPublicacionViewModel = viewModel(key = "editar-$objetoId", factory = fabrica(objetoId = objetoId)),
) {
    val estado by vm.estado.collectAsStateWithLifecycle()
    val f by vm.form.collectAsStateWithLifecycle()
    val accion by vm.accion.collectAsStateWithLifecycle()
    val evento by vm.evento.collectAsStateWithLifecycle()
    val categorias by vm.categorias.collectAsStateWithLifecycle()
    var calendario by remember { mutableStateOf(false) }
    LaunchedEffect(estado.datos) { estado.datos?.let(vm::iniciar) }
    LaunchedEffect(evento) { if (evento == "guardado") { vm.consumirEvento(); volver() } }

    Pagina("Editar publicación", volver) {
        Consulta(estado, vm::recargar) { objeto, _ ->
            if (!vm.editable) {
                AvisoEstado("Esta publicación está «${etiquetaEstadoObjeto(objeto.estado)}» y ya no se puede editar.", true)
                return@Consulta
            }
            val bloqueado = accion.ocupada
            OutlinedTextField(
                f.nombre, { vm.editar("nombre", it) }, label = { Text("Nombre del objeto") }, enabled = !bloqueado, singleLine = true,
                isError = f.errores["nombre"] != null, supportingText = f.errores["nombre"]?.let { { Text(it) } }, modifier = Modifier.fillMaxWidth(),
            )
            OutlinedTextField(
                f.descripcion, { vm.editar("descripcion", it) }, label = { Text("Descripción") }, enabled = !bloqueado, minLines = 3,
                isError = f.errores["descripcion"] != null, supportingText = f.errores["descripcion"]?.let { { Text(it) } }, modifier = Modifier.fillMaxWidth(),
            )
            Text("Categoría", style = MaterialTheme.typography.titleSmall)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                categorias.filter { it.estado || it.id == f.categoriaId }.forEach { c ->
                    FilterChip(selected = f.categoriaId == c.id, onClick = { vm.categoria(c.id) }, enabled = !bloqueado, label = { Text(c.nombre ?: "Categoría") })
                }
            }
            f.errores["categoria"]?.let { AvisoEstado(it, true) }
            OutlinedTextField(
                f.ubicacion, { vm.editar("ubicacion", it) }, label = { Text("Ubicación") }, enabled = !bloqueado, singleLine = true,
                isError = f.errores["ubicacion"] != null, supportingText = f.errores["ubicacion"]?.let { { Text(it) } }, modifier = Modifier.fillMaxWidth(),
            )
            OutlinedButton(onClick = { calendario = true }, enabled = !bloqueado, modifier = Modifier.fillMaxWidth()) { Text(f.fecha.ifBlank { "Elegir fecha" }) }
            f.errores["fecha"]?.let { AvisoEstado(it, true) }
            EstadoAccion(accion)
            BotonPrincipal("Guardar cambios", vm::guardar, !bloqueado)
        }
    }

    if (calendario) {
        val selector = rememberDatePickerState(selectableDates = object : SelectableDates {
            override fun isSelectableDate(utcTimeMillis: Long) = utcTimeMillis <= System.currentTimeMillis()
        })
        DatePickerDialog(
            onDismissRequest = { calendario = false },
            confirmButton = {
                TextButton(onClick = {
                    selector.selectedDateMillis?.let { ms ->
                        vm.editar("fecha", SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).apply { timeZone = TimeZone.getTimeZone("UTC") }.format(Date(ms)))
                    }
                    calendario = false
                }) { Text("Confirmar fecha") }
            },
            dismissButton = { TextButton(onClick = { calendario = false }) { Text("Cancelar") } },
        ) { DatePicker(selector, title = { Text("Fecha del objeto", Modifier.padding(20.dp)) }) }
    }
}
