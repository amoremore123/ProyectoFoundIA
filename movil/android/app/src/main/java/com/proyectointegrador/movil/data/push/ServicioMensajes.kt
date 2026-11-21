package com.proyectointegrador.movil.data.push

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
import com.google.firebase.messaging.FirebaseMessagingService
import com.google.firebase.messaging.RemoteMessage
import com.proyectointegrador.movil.MainActivity
import com.proyectointegrador.movil.di.Inyeccion
import com.proyectointegrador.movil.domain.CANAL_AVISOS
import com.proyectointegrador.movil.domain.CANAL_MENSAJES
import com.proyectointegrador.movil.domain.canalDeTipo
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Extra con el enlace del aviso; lo ponen tanto este servicio como el sistema (claves de `data`). */
const val EXTRA_ENLACE_PUSH = "enlace"

fun crearCanalesPush(contexto: Context) {
    if (Build.VERSION.SDK_INT < 26) return
    val gestor = contexto.getSystemService(NotificationManager::class.java)
    gestor.createNotificationChannel(NotificationChannel(CANAL_AVISOS, "Avisos", NotificationManager.IMPORTANCE_DEFAULT))
    gestor.createNotificationChannel(NotificationChannel(CANAL_MENSAJES, "Mensajes", NotificationManager.IMPORTANCE_HIGH))
}

class ServicioMensajes : FirebaseMessagingService() {
    private val alcance = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onNewToken(token: String) {
        // Firebase renovó el token: se vuelve a registrar solo si hay sesión.
        alcance.launch {
            val contenedor = runCatching { Inyeccion.contenedor }.getOrNull() ?: return@launch
            if (contenedor.cuenta.estadoSesion.autenticado) contenedor.sesionExterna.registrarDispositivo()
        }
    }

    override fun onMessageReceived(mensaje: RemoteMessage) {
        // Con la app abierta FCM no dibuja el aviso: se muestra aquí. Con la app cerrada lo muestra el sistema.
        val datos = mensaje.data
        val titulo = mensaje.notification?.title ?: return
        val cuerpo = mensaje.notification?.body.orEmpty()
        val gestor = NotificationManagerCompat.from(this)
        if (!gestor.areNotificationsEnabled() || Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) return
        crearCanalesPush(this)
        val abrir = Intent(this, MainActivity::class.java)
            .putExtra(EXTRA_ENLACE_PUSH, datos["enlace"].orEmpty())
            .addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val id = datos["notificacionId"]?.toIntOrNull() ?: System.currentTimeMillis().toInt()
        val destino = PendingIntent.getActivity(this, id, abrir, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        val aviso = NotificationCompat.Builder(this, canalDeTipo(datos["tipo"]))
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(titulo).setContentText(cuerpo)
            .setContentIntent(destino).setAutoCancel(true)
            .setVisibility(NotificationCompat.VISIBILITY_PRIVATE).build()
        try {
            gestor.notify(id, aviso)
        } catch (_: SecurityException) {
        }
    }
}
