package com.proyectointegrador.movil.ui.components

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.GoogleMap
import com.google.maps.android.compose.MapUiSettings
import com.google.maps.android.compose.Marker
import com.google.maps.android.compose.MarkerState
import com.google.maps.android.compose.rememberCameraPositionState
import com.proyectointegrador.movil.BuildConfig
import com.proyectointegrador.movil.domain.coordenadasValidas
import com.proyectointegrador.movil.domain.enlaceMapas

/**
 * Mapa con el punto de la publicación: Google Maps si el proyecto tiene clave (Google Cloud, semana 13) y, si no,
 * OpenStreetMap. En los dos casos se ofrece además abrir el punto en la app de mapas del sistema.
 */
@Composable
fun MapaUbicacion(latitud: Double, longitud: Double, titulo: String, modifier: Modifier = Modifier) {
    if (!coordenadasValidas(latitud, longitud)) return
    val contexto = LocalContext.current
    var error by remember { mutableStateOf(false) }
    Column(modifier, verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
        if (BuildConfig.MAPS_CONFIGURADO) {
            val punto = LatLng(latitud, longitud)
            val camara = rememberCameraPositionState { position = CameraPosition.fromLatLngZoom(punto, 16f) }
            GoogleMap(
                modifier = Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(18.dp)),
                cameraPositionState = camara,
                uiSettings = MapUiSettings(zoomControlsEnabled = false, scrollGesturesEnabled = false, zoomGesturesEnabled = false),
            ) { Marker(state = MarkerState(punto), title = titulo) }
        } else {
            // Sin clave de Google Maps (que pide facturación) se usa OpenStreetMap, gratis y sin clave.
            MapaOpenStreetMap(latitud, longitud, titulo)
        }
        OutlinedButton(
            onClick = {
                error = runCatching {
                    contexto.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(enlaceMapas(latitud, longitud, titulo))).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
                }.isFailure
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text("Abrir en mapas") }
        if (error) AvisoEstado("No hay una app de mapas para abrir este punto.", true)
    }
}
