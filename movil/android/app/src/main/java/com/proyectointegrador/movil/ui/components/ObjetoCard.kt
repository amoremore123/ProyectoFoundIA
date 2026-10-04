package com.proyectointegrador.movil.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.proyectointegrador.movil.model.Objeto
import com.proyectointegrador.movil.network.RetrofitClient
import com.proyectointegrador.movil.ui.theme.Border
import com.proyectointegrador.movil.ui.theme.ChipEncontradoBg
import com.proyectointegrador.movil.ui.theme.ChipPerdidoBg
import com.proyectointegrador.movil.ui.theme.Success
import com.proyectointegrador.movil.ui.theme.Danger

@Composable
fun TipoChip(tipo: String, modifier: Modifier = Modifier) {
    val esPerdido = tipo == "PERDIDO"
    val fondo = if (esPerdido) ChipPerdidoBg else ChipEncontradoBg
    val texto = if (esPerdido) Danger else Success
    val etiqueta = if (esPerdido) "PERDIDO" else "ENCONTRADO"

    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(fondo)
            .padding(horizontal = 8.dp, vertical = 3.dp)
    ) {
        Text(
            text = etiqueta,
            color = texto,
            style = MaterialTheme.typography.labelSmall
        )
    }
}

@Composable
fun ObjetoCard(
    objeto: Objeto,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val fotoUrl = RetrofitClient.urlAbsoluta(objeto.fotosSeguras.firstOrNull()?.url)

    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = Color.White),
        border = androidx.compose.foundation.BorderStroke(1.dp, Border),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(160.dp)
                    .background(Color(0xFFE2E8F0))
            ) {
                if (fotoUrl != null) {
                    AsyncImage(
                        model = fotoUrl,
                        contentDescription = objeto.nombre,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(text = "📦", style = MaterialTheme.typography.titleLarge)
                        Text(
                            text = "Sin foto",
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                }
            }

            Column(modifier = Modifier.padding(12.dp)) {
                TipoChip(tipo = objeto.tipoSeguro)
                Spacer(modifier = Modifier.size(8.dp))
                Text(
                    text = objeto.nombre,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                Spacer(modifier = Modifier.size(6.dp))
                Text(
                    text = "📍 ${objeto.ubicacion ?: "Sin ubicación"}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text(
                    text = "📅 ${objeto.fechaObjeto ?: "—"}",
                    style = MaterialTheme.typography.bodySmall
                )
            }
        }
    }
}
