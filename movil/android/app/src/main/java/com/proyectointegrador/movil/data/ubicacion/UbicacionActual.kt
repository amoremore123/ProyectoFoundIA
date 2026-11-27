package com.proyectointegrador.movil.data.ubicacion

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Address
import android.location.Geocoder
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.proyectointegrador.movil.domain.Coordenadas
import com.proyectointegrador.movil.domain.redondearCoordenada
import java.util.Locale
import kotlin.coroutines.resume
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

/** Resultado de pedir la ubicación actual del dispositivo. */
sealed interface ResultadoUbicacion {
    data class Exito(val coordenadas: Coordenadas) : ResultadoUbicacion
    /** El permiso se concedió pero el GPS y la red de ubicación están apagados. */
    data object Apagada : ResultadoUbicacion
    /** No llegó una posición a tiempo, por ejemplo bajo techo. */
    data object SinSenal : ResultadoUbicacion
}

/** Ubicación con el servicio del sistema (sin Google Play Services). Semana 11 del curso. */
object UbicacionActual {
    fun tienePermiso(contexto: Context): Boolean =
        listOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION).any {
            ContextCompat.checkSelfPermission(contexto, it) == PackageManager.PERMISSION_GRANTED
        }

    @SuppressLint("MissingPermission")
    suspend fun obtener(contexto: Context): ResultadoUbicacion {
        if (!tienePermiso(contexto)) return ResultadoUbicacion.SinSenal
        val gestor = contexto.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return ResultadoUbicacion.Apagada
        val activos = listOf(LocationManager.GPS_PROVIDER, LocationManager.NETWORK_PROVIDER)
            .filter { runCatching { gestor.isProviderEnabled(it) }.getOrDefault(false) }
        if (activos.isEmpty()) return ResultadoUbicacion.Apagada
        for (proveedor in activos) {
            val posicion = withTimeoutOrNull(8_000) {
                suspendCancellableCoroutine<Location?> { continuacion ->
                    val senal = CancellationSignal()
                    continuacion.invokeOnCancellation { senal.cancel() }
                    runCatching {
                        LocationManagerCompat.getCurrentLocation(gestor, proveedor, senal, ContextCompat.getMainExecutor(contexto)) {
                            if (continuacion.isActive) continuacion.resume(it)
                        }
                    }.onFailure { if (continuacion.isActive) continuacion.resume(null) }
                }
            } ?: runCatching { gestor.getLastKnownLocation(proveedor) }.getOrNull()
            if (posicion != null) return ResultadoUbicacion.Exito(Coordenadas(redondearCoordenada(posicion.latitude), redondearCoordenada(posicion.longitude)))
        }
        return ResultadoUbicacion.SinSenal
    }

    /** Dirección legible de unas coordenadas. Null si el dispositivo no puede resolverla; entonces se usan las coordenadas. */
    suspend fun direccionDe(contexto: Context, coordenadas: Coordenadas): String? {
        if (!Geocoder.isPresent()) return null
        val geocodificador = Geocoder(contexto, Locale("es", "PE"))
        val direcciones: List<Address>? = try {
            if (Build.VERSION.SDK_INT >= 33) {
                withTimeoutOrNull(6_000) {
                    suspendCancellableCoroutine { continuacion ->
                        geocodificador.getFromLocation(coordenadas.latitud, coordenadas.longitud, 1, object : Geocoder.GeocodeListener {
                            override fun onGeocode(resultado: MutableList<Address>) { if (continuacion.isActive) continuacion.resume(resultado) }
                            override fun onError(mensaje: String?) { if (continuacion.isActive) continuacion.resume(null) }
                        })
                    }
                }
            } else withContext(Dispatchers.IO) {
                @Suppress("DEPRECATION")
                geocodificador.getFromLocation(coordenadas.latitud, coordenadas.longitud, 1)
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            null
        }
        return direcciones?.firstOrNull()?.getAddressLine(0)?.trim()?.takeIf { it.isNotBlank() }
    }
}
