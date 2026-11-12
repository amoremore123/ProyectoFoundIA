package com.proyectointegrador.movil.data.avisos

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.*
import com.proyectointegrador.movil.MainActivity
import com.proyectointegrador.movil.di.Inyeccion
import com.proyectointegrador.movil.domain.ErrorDatos
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.CancellationException

private const val CANAL_RECLAMOS = "foundia.reclamos"

fun programarAvisosReclamo(contexto: Context) {
    val trabajo = PeriodicWorkRequestBuilder<AvisosReclamoWorker>(15, TimeUnit.MINUTES)
        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
        .build()
    WorkManager.getInstance(contexto).enqueueUniquePeriodicWork(CANAL_RECLAMOS, ExistingPeriodicWorkPolicy.KEEP, trabajo)
}

class AvisosReclamoWorker(contexto: Context, parametros: WorkerParameters) : CoroutineWorker(contexto, parametros) {
    override suspend fun doWork(): Result {
        val contexto = applicationContext
        val repo = Inyeccion.contenedor.cuenta
        val sesion = repo.estadoSesion
        if (!sesion.autenticado) return Result.success()
        val gestor = NotificationManagerCompat.from(contexto)
        if (!gestor.areNotificationsEnabled() || Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(contexto, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return Result.success()
        try {
            val cuenta = repo.perfil()
            if (!repo.preferencias().reclamos || repo.estadoSesion.revision != sesion.revision) return Result.success()
            val preferencias = contexto.getSharedPreferences("avance_avisos_reclamo", Context.MODE_PRIVATE)
            val clave = "ultimo_" + cuenta.id
            val ultimo = preferencias.getLong(clave, 0)
            val formato = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.ROOT).apply { timeZone = TimeZone.getTimeZone("UTC") }
            val desde = formato.format(Date(System.currentTimeMillis() - TimeUnit.DAYS.toMillis(7)))
            val avisos = repo.novedadesReclamos(desde, ultimo)
            if (avisos.isEmpty() || repo.estadoSesion.revision != sesion.revision) return Result.success()
            if (Build.VERSION.SDK_INT >= 26) {
                contexto.getSystemService(NotificationManager::class.java).createNotificationChannel(
                    NotificationChannel(CANAL_RECLAMOS, "Reclamos de biblioteca", NotificationManager.IMPORTANCE_DEFAULT)
                )
            }
            val destino = PendingIntent.getActivity(contexto, 50,
                Intent(contexto, MainActivity::class.java).putExtra("avisosReclamo", true)
                    .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP),
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            // La bandeja autenticada muestra los detalles. La pantalla bloqueada recibe solo un texto genérico.
            val aviso = NotificationCompat.Builder(contexto, CANAL_RECLAMOS)
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setContentTitle("Novedades de tus reclamos")
                .setContentText("Abre FoundIA para revisar el estado de tus reclamos.")
                .setContentIntent(destino).setVisibility(NotificationCompat.VISIBILITY_PRIVATE).setAutoCancel(true).build()
            gestor.notify(50, aviso)
            preferencias.edit().putLong(clave, avisos.maxOf { it.id }).apply()
            return Result.success()
        } catch (cancelado: CancellationException) {
            throw cancelado
        } catch (error: ErrorDatos) {
            return if (error.estadoHttp == 401 || error.estadoHttp == 403) Result.success() else Result.retry()
        } catch (_: SecurityException) {
            return Result.success()
        }
    }
}
