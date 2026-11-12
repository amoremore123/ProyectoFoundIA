package com.proyectointegrador.movil.ui.screens.publicaciones

import androidx.camera.core.CameraSelector
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
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
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage

@androidx.annotation.OptIn(ExperimentalGetImage::class)
@Composable
fun EscanerCustodia(cerrar: () -> Unit, detectado: (String) -> Unit) {
    val contexto = LocalContext.current
    val ciclo = LocalLifecycleOwner.current
    val vista = remember(contexto) { PreviewView(contexto).apply { implementationMode = PreviewView.ImplementationMode.COMPATIBLE } }
    val alDetectar by rememberUpdatedState(detectado)
    var error by remember { mutableStateOf<String?>(null) }
    DisposableEffect(ciclo, vista) {
        val ejecutor = ContextCompat.getMainExecutor(contexto)
        val futuro = ProcessCameraProvider.getInstance(contexto)
        val preview = Preview.Builder().build().apply { surfaceProvider = vista.surfaceProvider }
        val analisis = ImageAnalysis.Builder().setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST).build()
        val lector = BarcodeScanning.getClient(BarcodeScannerOptions.Builder().setBarcodeFormats(Barcode.FORMAT_QR_CODE).build())
        var proveedor: ProcessCameraProvider? = null
        var activa = true
        var entregado = false
        analisis.setAnalyzer(ejecutor) { cuadro ->
            val imagen = cuadro.image
            if (!activa || entregado || imagen == null) cuadro.close()
            else lector.process(InputImage.fromMediaImage(imagen, cuadro.imageInfo.rotationDegrees))
                .addOnSuccessListener(ejecutor) { codigos ->
                    val valor = codigos.singleOrNull()?.rawValue?.takeIf { it.isNotBlank() }
                    if (activa && !entregado && valor != null) {
                        entregado = true
                        alDetectar(valor)
                    }
                }.addOnCompleteListener(ejecutor) { cuadro.close() }
        }
        futuro.addListener({
            if (activa) try {
                proveedor = futuro.get()
                proveedor?.bindToLifecycle(ciclo, CameraSelector.DEFAULT_BACK_CAMERA, preview, analisis)
            } catch (_: Exception) {
                error = "No se pudo abrir la cámara. Puedes ingresar el código."
            }
        }, ejecutor)
        onDispose {
            activa = false
            analisis.clearAnalyzer()
            proveedor?.unbind(preview, analisis)
            lector.close()
        }
    }
    AlertDialog(onDismissRequest = cerrar, title = { Text("Escanear código QR") },
        text = {
            Column {
                AndroidView(factory = { vista }, modifier = Modifier.fillMaxWidth().height(240.dp))
                error?.let { Text(it) }
            }
        },
        confirmButton = { TextButton(onClick = cerrar) { Text("Cancelar") } })
}
