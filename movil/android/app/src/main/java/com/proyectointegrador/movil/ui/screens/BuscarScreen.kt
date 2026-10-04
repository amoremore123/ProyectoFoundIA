package com.proyectointegrador.movil.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import com.proyectointegrador.movil.ui.components.ObjetoCard
import com.proyectointegrador.movil.ui.theme.Background
import com.proyectointegrador.movil.ui.theme.Danger
import com.proyectointegrador.movil.ui.theme.Primary
import com.proyectointegrador.movil.ui.theme.Primary50
import com.proyectointegrador.movil.ui.viewmodel.ObjetosViewModel

private val FILTROS = listOf(
    "TODO" to null,
    "PERDIDO" to "PERDIDO",
    "ENCONTRADO" to "ENCONTRADO"
)

@Composable
fun BuscarScreen(
    onObjetoClick: (Long) -> Unit,
    viewModel: ObjetosViewModel = viewModel()
) {
    val state by viewModel.state.collectAsState()
    var texto by remember { mutableStateOf("") }
    var tipo by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(texto, tipo) {
        viewModel.buscar(q = texto, tipo = tipo)
    }

    Scaffold(containerColor = Background) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp)
        ) {
            Text(
                text = "Buscar",
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.padding(vertical = 12.dp)
            )

            OutlinedTextField(
                value = texto,
                onValueChange = { texto = it },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                placeholder = { Text("🔍 Buscar objeto...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                shape = RoundedCornerShape(10.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = Primary,
                    unfocusedBorderColor = MaterialTheme.colorScheme.outline
                )
            )

            Row(
                modifier = Modifier.padding(top = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                FILTROS.forEach { (etiqueta, valor) ->
                    FiltroChip(
                        etiqueta = etiqueta,
                        seleccionado = tipo == valor,
                        onClick = { tipo = valor }
                    )
                }
            }

            when {
                state.cargando -> Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(48.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    CircularProgressIndicator()
                }

                state.error != null -> Text(
                    text = "⚠️ ${state.error.orEmpty()}",
                    color = Danger,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 32.dp)
                )

                state.objetos.isEmpty() -> Text(
                    text = "Sin resultados.\nPrueba con otra palabra o filtro.",
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 40.dp)
                )

                else -> LazyColumn(
                    contentPadding = PaddingValues(top = 16.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(state.objetos, key = { it.id }) { objeto ->
                        ObjetoCard(objeto = objeto, onClick = { onObjetoClick(objeto.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun FiltroChip(
    etiqueta: String,
    seleccionado: Boolean,
    onClick: () -> Unit
) {
    val colorBorde = if (seleccionado) Primary else MaterialTheme.colorScheme.outline
    Text(
        text = etiqueta,
        style = MaterialTheme.typography.labelLarge,
        color = if (seleccionado) Primary else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .background(
                color = if (seleccionado) Primary50 else MaterialTheme.colorScheme.surface,
                shape = RoundedCornerShape(20.dp)
            )
            .border(
                width = 1.dp,
                color = colorBorde,
                shape = RoundedCornerShape(20.dp)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp)
    )
}
