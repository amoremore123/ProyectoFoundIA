package com.proyectointegrador.movil.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.proyectointegrador.movil.di.fabrica
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.proyectointegrador.movil.ui.theme.Background
import com.proyectointegrador.movil.ui.theme.Danger
import com.proyectointegrador.movil.ui.theme.Primary
import com.proyectointegrador.movil.ui.theme.Primary50
import com.proyectointegrador.movil.ui.theme.SurfaceWhite
import com.proyectointegrador.movil.ui.viewmodel.PublicarViewModel
import com.proyectointegrador.movil.ui.sprint.Consulta
import com.proyectointegrador.movil.ui.sprint.EstadoAccion

@Composable
fun PublicarScreen(onPublicado:(Long)->Unit={},viewModel:PublicarViewModel=viewModel(factory=fabrica())) {
    val estado by viewModel.estado.collectAsStateWithLifecycle()
    val form by viewModel.form.collectAsStateWithLifecycle()
    val accion by viewModel.accion.collectAsStateWithLifecycle()
    val evento by viewModel.evento.collectAsStateWithLifecycle()
    LaunchedEffect(evento){evento?.toLongOrNull()?.let {viewModel.consumirEvento();onPublicado(it)}}
    val categorias=estado.datos?.datos.orEmpty()
    val tipo=form.tipo;val nombre=form.nombre;val descripcion=form.descripcion
    val categoriaId=form.categoriaId;val ubicacion=form.ubicacion;val fecha=form.fecha;val errores=form.errores

    Scaffold(
        containerColor = Background,
        snackbarHost = {}
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 12.dp)
        ) {
            Text(
                text = "Publicar objeto",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(bottom = 16.dp)
            )

            Text(
                text = "¿Qué tipo de publicación es?",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface
            )
            Row(
                modifier = Modifier.padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OpcionTipo(
                    etiqueta = "Perdí un objeto",
                    seleccionada = tipo == "PERDIDO",
                    onClick = { viewModel.editar("tipo","PERDIDO") }
                )
                OpcionTipo(
                    etiqueta = "Encontré un objeto",
                    seleccionada = tipo == "ENCONTRADO",
                    onClick = { viewModel.editar("tipo","ENCONTRADO") }
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            CampoTexto(
                valor = nombre,
                onValueChange = { viewModel.editar("nombre",it) },
                label = "Nombre *",
                error = errores["nombre"]
            )

            CampoTexto(
                valor = descripcion,
                onValueChange = { viewModel.editar("descripcion",it) },
                label = "Descripción *",
                error = errores["descripcion"],
                minLines = 3
            )

            Text(
                text = "Categoría *",
                style = MaterialTheme.typography.labelLarge,
                modifier = Modifier.padding(top = 12.dp, bottom = 6.dp)
            )
            estado.datos?.aviso?.let {Text(it)}
            if (categorias.isEmpty()) {
                Text(
                    text = "Cargando categorías…",
                    style = MaterialTheme.typography.bodySmall
                )
            } else {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                ) {
                    categorias.forEach { categoria ->
                        val seleccionada = categoriaId == categoria.id
                        Text(
                            text = categoria.nombre ?: "—",
                            style = MaterialTheme.typography.labelLarge,
                            color = if (seleccionada) Primary else MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier
                                .background(
                                    color = if (seleccionada) Primary50 else SurfaceWhite,
                                    shape = RoundedCornerShape(20.dp)
                                )
                                .border(
                                    width = 1.dp,
                                    color = if (seleccionada) Primary else MaterialTheme.colorScheme.outline,
                                    shape = RoundedCornerShape(20.dp)
                                )
                                .clickable {
                                    viewModel.categoria(categoria.id)

                                }
                                .padding(horizontal = 14.dp, vertical = 8.dp)
                        )
                    }
                }
            }
            ErrorTexto(errores["categoria"])

            CampoTexto(
                valor = ubicacion,
                onValueChange = { viewModel.editar("ubicacion",it) },
                label = "Ubicación *",
                error = errores["ubicacion"]
            )

            CampoTexto(
                valor = fecha,
                onValueChange = { viewModel.editar("fecha",it) },
                label = "Fecha * (yyyy-MM-dd)",
                error = errores["fecha"]
            )

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "📷 Foto y coordenadas: próximamente",
                style = MaterialTheme.typography.bodySmall
            )

            estado.error?.let { Text(it,color=Danger);Button(onClick=viewModel::recargar){Text("Reintentar categorías")} }
            EstadoAccion(accion)
            Spacer(modifier = Modifier.height(24.dp))
            Button(
                onClick = viewModel::publicar,
                enabled = !accion.ocupada && !estado.cargando && estado.error == null,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                shape = RoundedCornerShape(10.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Primary)
            ) {
                Text("PUBLICAR", color = SurfaceWhite)
            }
        }
    }
}

@Composable
private fun OpcionTipo(
    etiqueta: String,
    seleccionada: Boolean,
    onClick: () -> Unit
) {
    Text(
        text = etiqueta,
        style = MaterialTheme.typography.labelLarge,
        color = if (seleccionada) Primary else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .background(
                color = if (seleccionada) Primary50 else SurfaceWhite,
                shape = RoundedCornerShape(10.dp)
            )
            .border(
                width = 1.dp,
                color = if (seleccionada) Primary else MaterialTheme.colorScheme.outline,
                shape = RoundedCornerShape(10.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 12.dp)
    )
}

@Composable
private fun CampoTexto(
    valor: String,
    onValueChange: (String) -> Unit,
    label: String,
    error: String?,
    minLines: Int = 1
) {
    Column(modifier = Modifier.padding(top = 12.dp)) {
        OutlinedTextField(
            value = valor,
            onValueChange = onValueChange,
            label = { Text(label) },
            modifier = Modifier.fillMaxWidth(),
            minLines = minLines,
            isError = error != null,
            shape = RoundedCornerShape(10.dp),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = Primary,
                unfocusedBorderColor = MaterialTheme.colorScheme.outline
            )
        )
        ErrorTexto(error)
    }
}

@Composable
private fun ErrorTexto(error: String?) {
    if (error != null) {
        Text(
            text = error,
            color = Danger,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 4.dp)
        )
    }
}
