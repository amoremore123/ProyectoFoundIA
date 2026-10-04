package com.proyectointegrador.movil.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.proyectointegrador.movil.network.RetrofitClient
import com.proyectointegrador.movil.ui.components.TipoChip
import com.proyectointegrador.movil.ui.theme.Background
import com.proyectointegrador.movil.ui.theme.Border
import com.proyectointegrador.movil.ui.theme.Danger
import com.proyectointegrador.movil.ui.theme.Primary
import com.proyectointegrador.movil.ui.theme.SurfaceWhite
import com.proyectointegrador.movil.ui.viewmodel.ObjetosViewModel

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DetalleScreen(
    objetoId: Long,
    onBack: () -> Unit,
    onObjetoClick: (Long) -> Unit,
    viewModel: ObjetosViewModel = viewModel()
) {
    val detalle by viewModel.detalle.collectAsState()
    val coincidencias by viewModel.coincidencias.collectAsState()
    val cargando by viewModel.cargandoDetalle.collectAsState()
    val error by viewModel.errorDetalle.collectAsState()

    LaunchedEffect(objetoId) { viewModel.cargarDetalle(objetoId) }

    Scaffold(
        containerColor = Background,
        topBar = {
            TopAppBar(
                title = { Text("Detalle") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Volver")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SurfaceWhite,
                    titleContentColor = MaterialTheme.colorScheme.onSurface,
                    navigationIconContentColor = MaterialTheme.colorScheme.onSurface
                )
            )
        }
    ) { padding ->
        when {
            cargando -> Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator()
            }

            error != null -> Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Text(
                    text = "⚠️ ${error.orEmpty()}",
                    color = Danger,
                    textAlign = TextAlign.Center
                )
                Button(
                    onClick = { viewModel.cargarDetalle(objetoId) },
                    modifier = Modifier.padding(top = 16.dp)
                ) {
                    Text("Reintentar")
                }
            }

            detalle != null -> DetalleContenido(
                objeto = detalle!!,
                coincidencias = coincidencias,
                onObjetoClick = onObjetoClick,
                contentPadding = padding
            )
        }
    }
}

@Composable
private fun DetalleContenido(
    objeto: com.proyectointegrador.movil.model.Objeto,
    coincidencias: List<com.proyectointegrador.movil.model.CoincidenciaSugerida>,
    onObjetoClick: (Long) -> Unit,
    contentPadding: PaddingValues
) {
    val fotoUrl = RetrofitClient.urlAbsoluta(objeto.fotosSeguras.firstOrNull()?.url)

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .padding(contentPadding),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(200.dp)
                    .background(color = androidx.compose.ui.graphics.Color(0xFFE2E8F0), shape = RoundedCornerShape(14.dp))
            ) {
                if (fotoUrl != null) {
                    AsyncImage(
                        model = fotoUrl,
                        contentDescription = objeto.nombre,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Text(
                        text = "📦",
                        modifier = Modifier.align(Alignment.Center),
                        style = MaterialTheme.typography.headlineMedium
                    )
                }
            }
        }

        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TipoChip(tipo = objeto.tipoSeguro)
                objeto.categoria?.nombre?.let { categoria ->
                    Text(
                        text = categoria,
                        style = MaterialTheme.typography.labelSmall,
                        color = Primary,
                        modifier = Modifier
                            .background(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(6.dp))
                            .padding(horizontal = 8.dp, vertical = 3.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                text = objeto.nombre,
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }

        item {
            Text(
                text = objeto.descripcion.orEmpty().ifBlank { "Sin descripción" },
                style = MaterialTheme.typography.bodyMedium
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(text = "📍 ${objeto.ubicacion ?: "Sin ubicación"}", style = MaterialTheme.typography.bodyMedium)
            Text(text = "📅 ${objeto.fechaObjeto ?: "—"}", style = MaterialTheme.typography.bodyMedium)
            Text(
                text = "👤 Publicado por ${objeto.publicadoPor?.nombreCompleto ?: "—"}",
                style = MaterialTheme.typography.bodyMedium
            )
        }

        if (coincidencias.isNotEmpty()) {
            item {
                Text(
                    text = "Coincidencias sugeridas",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
            items(coincidencias, key = { it.objetoId }) { coincidencia ->
                Card(
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = SurfaceWhite),
                    border = androidx.compose.foundation.BorderStroke(1.dp, Border),
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onObjetoClick(coincidencia.objetoId) }
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = coincidencia.nombre ?: "Objeto",
                                style = MaterialTheme.typography.titleMedium
                            )
                            Text(
                                text = coincidencia.nivel ?: "—",
                                style = MaterialTheme.typography.labelLarge,
                                color = Primary
                            )
                        }
                        Text(
                            text = "📍 ${coincidencia.ubicacion ?: "Sin ubicación"} · 📅 ${coincidencia.fechaObjeto ?: "—"}",
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = "Similitud: ${coincidencia.porcentaje ?: 0.0}%",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }
        } else {
            item {
                Text(
                    text = "Sin coincidencias sugeridas por ahora.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp)
                )
            }
        }
    }
}
