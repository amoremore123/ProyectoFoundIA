package com.proyectointegrador.movil.ui.components

import android.view.MotionEvent
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import org.osmdroid.config.Configuration
import org.osmdroid.tileprovider.tilesource.TileSourceFactory
import org.osmdroid.util.GeoPoint
import org.osmdroid.views.MapView
import org.osmdroid.views.overlay.Marker
import java.io.File

/**
 * Mapa de OpenStreetMap, gratis y sin clave: es el respaldo cuando el proyecto no tiene clave de Google Maps
 * (que exige una cuenta de facturación). Muestra el punto de la publicación; no es interactivo para no robar el desplazamiento.
 */
@Composable
fun MapaOpenStreetMap(latitud: Double, longitud: Double, titulo: String, modifier: Modifier = Modifier) {
    val contexto = LocalContext.current
    remember {
        // Las baldosas se guardan en la caché de la app: no hace falta permiso de almacenamiento.
        val base = File(contexto.cacheDir, "osmdroid")
        Configuration.getInstance().apply {
            userAgentValue = contexto.packageName
            osmdroidBasePath = base
            osmdroidTileCache = File(base, "tiles")
        }
        true
    }
    val mapa = remember {
        MapView(contexto).apply {
            setTileSource(TileSourceFactory.MAPNIK)
            setMultiTouchControls(false)
            zoomController.setVisibility(org.osmdroid.views.CustomZoomButtonsController.Visibility.NEVER)
            setOnTouchListener { _, evento -> evento.action == MotionEvent.ACTION_MOVE && false }
        }
    }
    DisposableEffect(mapa) {
        mapa.onResume()
        onDispose { mapa.onPause(); mapa.onDetach() }
    }
    Column(modifier) {
        AndroidView(
            modifier = Modifier.fillMaxWidth().height(180.dp).clip(RoundedCornerShape(18.dp)),
            factory = { mapa },
            update = { vista ->
                val punto = GeoPoint(latitud, longitud)
                vista.controller.setZoom(16.0)
                vista.controller.setCenter(punto)
                vista.overlays.clear()
                vista.overlays.add(Marker(vista).apply { position = punto; this.title = titulo; setAnchor(Marker.ANCHOR_CENTER, Marker.ANCHOR_BOTTOM) })
                vista.invalidate()
            }
        )
        // La licencia de los datos de OpenStreetMap exige esta atribución.
        Text("© Colaboradores de OpenStreetMap", style = MaterialTheme.typography.labelSmall)
    }
}
