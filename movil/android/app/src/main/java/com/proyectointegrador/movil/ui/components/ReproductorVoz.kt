package com.proyectointegrador.movil.ui.components

import android.content.Context
import android.media.AudioAttributes
import android.media.MediaPlayer
import java.io.File

/**
 * Reproduce el audio MP3 de la voz natural de FINDI (la misma que usa la web). Se guarda un archivo temporal en la caché de la
 * app que se borra al terminar o al detener. Solo suena un audio a la vez.
 */
class ReproductorVoz(private val contexto: Context) {
    private var reproductor: MediaPlayer? = null
    private var archivo: File? = null

    val sonando: Boolean get() = runCatching { reproductor?.isPlaying == true }.getOrDefault(false)

    /** Devuelve false si el audio no se pudo reproducir (entonces conviene usar la voz del teléfono). */
    fun reproducir(audio: ByteArray, alTerminar: () -> Unit): Boolean {
        detener()
        return try {
            val temporal = File.createTempFile("voz-findi", ".mp3", contexto.cacheDir).also { it.writeBytes(audio) }
            archivo = temporal
            val jugador = MediaPlayer().apply {
                setAudioAttributes(AudioAttributes.Builder().setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).setUsage(AudioAttributes.USAGE_MEDIA).build())
                setDataSource(temporal.absolutePath)
                setOnCompletionListener { detener(); alTerminar() }
                setOnErrorListener { _, _, _ -> detener(); alTerminar(); true }
                prepare()
                start()
            }
            reproductor = jugador
            true
        } catch (_: Exception) {
            detener()
            false
        }
    }

    fun detener() {
        runCatching { reproductor?.stop() }
        runCatching { reproductor?.release() }
        reproductor = null
        archivo?.let { runCatching { it.delete() } }
        archivo = null
    }
}
