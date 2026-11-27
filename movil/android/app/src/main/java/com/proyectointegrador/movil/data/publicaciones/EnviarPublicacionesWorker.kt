package com.proyectointegrador.movil.data.publicaciones

import android.content.Context
import androidx.work.*
import com.proyectointegrador.movil.di.Inyeccion
import java.util.concurrent.TimeUnit

private const val TRABAJO = "foundia.publicaciones.pendientes"

/** H65: pide enviar las publicaciones en espera en cuanto haya red. Se puede llamar muchas veces: solo hay un trabajo. */
fun programarEnvioPublicaciones(contexto: Context) {
    val trabajo = OneTimeWorkRequestBuilder<EnviarPublicacionesWorker>()
        .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
        .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 30, TimeUnit.SECONDS)
        .build()
    WorkManager.getInstance(contexto).enqueueUniqueWork(TRABAJO, ExistingWorkPolicy.KEEP, trabajo)
}

class EnviarPublicacionesWorker(contexto: Context, parametros: WorkerParameters) : CoroutineWorker(contexto, parametros) {
    override suspend fun doWork(): Result {
        val resultado = Inyeccion.contenedor.cuenta.enviarPublicacionesPendientes()
        return if (resultado.reintentar) Result.retry() else Result.success()
    }
}
