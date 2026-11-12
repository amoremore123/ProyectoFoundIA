package com.proyectointegrador.movil.ui.components

import android.net.Uri
import android.util.Size
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.LocalLifecycleOwner
import java.io.File

@Composable
fun CamaraPublicacion(cerrar: () -> Unit, usar: (Uri) -> Unit) {
    val contexto = LocalContext.current
    val ciclo = LocalLifecycleOwner.current
    val vista = remember(contexto) { PreviewView(contexto).apply { implementationMode = PreviewView.ImplementationMode.COMPATIBLE } }
    val captura = remember {
        ImageCapture.Builder().setJpegQuality(80).setResolutionSelector(
            ResolutionSelector.Builder().setResolutionStrategy(
                ResolutionStrategy(Size(1600, 1200), ResolutionStrategy.FALLBACK_RULE_CLOSEST_LOWER_THEN_HIGHER)
            ).build()
        ).build()
    }
    val ejecutor = remember(contexto) { ContextCompat.getMainExecutor(contexto) }
    var lista by remember { mutableStateOf(false) }
    var tomando by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }
    DisposableEffect(ciclo, vista, captura) {
        val futuro = ProcessCameraProvider.getInstance(contexto)
        val preview = Preview.Builder().build().apply { surfaceProvider = vista.surfaceProvider }
        var activa = true
        var proveedor: ProcessCameraProvider? = null
        futuro.addListener({
            if (activa) {
                try {
                    proveedor = futuro.get()
                    proveedor?.bindToLifecycle(ciclo, CameraSelector.DEFAULT_BACK_CAMERA, preview, captura)
                    lista = true
                } catch (_: Exception) {
                    error = "No se pudo abrir la cámara. Puedes elegir una foto de la galería."
                }
            }
        }, ejecutor)
        onDispose {
            activa = false
            proveedor?.unbind(preview, captura)
        }
    }

    AlertDialog(
        onDismissRequest = { if (!tomando) cerrar() },
        title = { Text("Tomar fotografía") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                AndroidView(factory = { vista }, modifier = Modifier.fillMaxWidth().height(240.dp))
                error?.let { AvisoEstado(it, true) }
            }
        },
        dismissButton = { TextButton(onClick = cerrar, enabled = !tomando) { Text("Cancelar") } },
        confirmButton = {
            TextButton(enabled = lista && !tomando, onClick = {
                val archivo = runCatching {
                    File.createTempFile("foto-", ".jpg", File(contexto.cacheDir, "capturas").apply { mkdirs() })
                }.getOrElse {
                    error = "No se pudo preparar la captura."
                    return@TextButton
                }
                vista.display?.let { captura.targetRotation = it.rotation }
                tomando = true
                captura.takePicture(ImageCapture.OutputFileOptions.Builder(archivo).build(), ejecutor,
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(resultado: ImageCapture.OutputFileResults) {
                            tomando = false
                            if (archivo.length() !in 1L..5_242_880L) {
                                archivo.delete()
                                error = "La foto supera el límite de 5 MiB. Puedes repetirla o usar la galería."
                                return
                            }
                            usar(FileProvider.getUriForFile(contexto, contexto.packageName + ".capturas", archivo))
                        }

                        override fun onError(fallo: ImageCaptureException) {
                            archivo.delete()
                            tomando = false
                            error = "No se pudo tomar la foto. Vuelve a intentarlo."
                        }
                    }
                )
            }) { Text(if (tomando) "Guardando…" else "Tomar foto") }
        }
    )
}
