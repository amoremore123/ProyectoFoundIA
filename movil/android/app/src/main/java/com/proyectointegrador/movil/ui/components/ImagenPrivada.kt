package com.proyectointegrador.movil.ui.components

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import com.proyectointegrador.movil.di.Inyeccion

private sealed interface EstadoImagen {
    data object Cargando : EstadoImagen
    data object Error : EstadoImagen
    data class Lista(val imagen: ImageBitmap) : EstadoImagen
}

/**
 * H62 y H64: muestra una foto privada de la API (se pide con la sesión). No sirve para direcciones públicas ni externas:
 * el repositorio rechaza cualquier ruta que no sea una de las fotos privadas del chat.
 */
@Composable
fun ImagenPrivada(
    ruta: String, descripcion: String, modifier: Modifier = Modifier,
    escala: ContentScale = ContentScale.Crop, textoError: String = "Foto no disponible"
) {
    val estado by produceState<EstadoImagen>(EstadoImagen.Cargando, ruta) {
        value = try {
            val bytes = Inyeccion.contenedor.cuenta.fotoPrivada(ruta)
            val mapa = BitmapFactory.decodeByteArray(bytes, 0, bytes.size)
            if (mapa == null) EstadoImagen.Error else EstadoImagen.Lista(mapa.asImageBitmap())
        } catch (e: kotlinx.coroutines.CancellationException) {
            throw e
        } catch (_: Exception) {
            EstadoImagen.Error
        }
    }
    val marco = remember { modifier }
    Box(marco, contentAlignment = Alignment.Center) {
        when (val e = estado) {
            EstadoImagen.Cargando -> CircularProgressIndicator(Modifier.size(24.dp), strokeWidth = 2.dp)
            EstadoImagen.Error -> Text(textoError, style = MaterialTheme.typography.bodySmall)
            is EstadoImagen.Lista -> Image(e.imagen, descripcion, Modifier.fillMaxSize(), contentScale = escala)
        }
    }
}
