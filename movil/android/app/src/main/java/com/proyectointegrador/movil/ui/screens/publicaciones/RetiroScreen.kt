package com.proyectointegrador.movil.ui.screens.publicaciones

import android.Manifest
import android.content.pm.PackageManager
import android.graphics.Bitmap
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import com.proyectointegrador.movil.di.fabrica
import com.proyectointegrador.movil.model.QrRetiroPropio
import com.proyectointegrador.movil.ui.components.*
import com.proyectointegrador.movil.ui.viewmodel.RetiroViewModel

@Composable
fun RetiroScreen(volver: () -> Unit, vm: RetiroViewModel = viewModel(factory = fabrica())) {
    val estado by vm.estado.collectAsStateWithLifecycle()
    val accion by vm.accion.collectAsStateWithLifecycle()
    val evento by vm.evento.collectAsStateWithLifecycle()
    var etiqueta by remember { mutableStateOf("") }
    var token by remember { mutableStateOf("") }
    var escanear by remember { mutableStateOf<Boolean?>(null) }
    var solicitado by remember { mutableStateOf(false) }
    var confirmar by remember { mutableStateOf(false) }
    var aviso by remember { mutableStateOf<String?>(null) }
    val contexto = LocalContext.current
    val permiso = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { concedido ->
        if (concedido) escanear = solicitado else aviso = "Sin permiso de cámara. Puedes ingresar los códigos."
    }

    LaunchedEffect(evento) {
        if (evento != null) {
            etiqueta = ""
            token = ""
            vm.consumirEvento()
        }
    }
    Pagina("Registrar entrega", volver) {
        Consulta(estado, vm::recargar) { usuario, _ ->
            if (usuario.rol != "BIBLIOTECA") {
                AvisoEstado("Esta función corresponde al personal de biblioteca.", true)
                return@Consulta
            }
            fun iniciar(esEtiqueta: Boolean) {
                aviso = null
                if (ContextCompat.checkSelfPermission(contexto, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
                    escanear = esEtiqueta
                } else {
                    solicitado = esEtiqueta
                    permiso.launch(Manifest.permission.CAMERA)
                }
            }
            Text("Escanea la etiqueta del objeto y el QR que presenta la persona.")
            aviso?.let { AvisoEstado(it) }
            OutlinedTextField(etiqueta, { etiqueta = it.take(500) }, label = { Text("Etiqueta de custodia") },
                enabled = !accion.ocupada, modifier = Modifier.fillMaxWidth(), visualTransformation = PasswordVisualTransformation())
            OutlinedButton(onClick = { iniciar(true) }, enabled = !accion.ocupada) { Text("Escanear etiqueta") }
            OutlinedTextField(token, { token = it.take(64) }, label = { Text("Código de retiro") },
                enabled = !accion.ocupada, modifier = Modifier.fillMaxWidth(), visualTransformation = PasswordVisualTransformation())
            OutlinedButton(onClick = { iniciar(false) }, enabled = !accion.ocupada) { Text("Escanear QR de retiro") }
            EstadoAccion(accion)
            BotonPrincipal("Revisar entrega", { confirmar = true }, etiqueta.isNotBlank() && token.isNotBlank() && !accion.ocupada)
        }
    }
    escanear?.let { esEtiqueta ->
        EscanerCustodia(cerrar = { escanear = null }, detectado = { valor ->
            if (esEtiqueta && valor.length <= 500) etiqueta = valor
            else if (!esEtiqueta && valor.length <= 64) token = valor
            else aviso = "El QR no corresponde al campo seleccionado."
            escanear = null
        })
    }
    if (confirmar) Confirmacion("Confirmar entrega", "El servidor validará ambos QR y registrará el retiro.",
        accion, { confirmar = false }, {
            confirmar = false
            vm.entregar(token.trim(), etiqueta.trim())
        })
}

@Composable
fun MostrarQrRetiro(qr: QrRetiroPropio, cerrar: () -> Unit) {
    val imagen = remember(qr.token) {
        val matriz = QRCodeWriter().encode(qr.token, BarcodeFormat.QR_CODE, 384, 384)
        val pixeles = IntArray(384 * 384) { i -> if (matriz[i % 384, i / 384]) android.graphics.Color.BLACK else android.graphics.Color.WHITE }
        Bitmap.createBitmap(pixeles, 384, 384, Bitmap.Config.ARGB_8888).asImageBitmap()
    }
    AlertDialog(
        onDismissRequest = cerrar,
        title = { Text("QR de retiro") },
        text = {
            Column {
                Image(imagen, "Código QR de retiro de un solo uso", Modifier.fillMaxWidth().height(220.dp))
                Text("Vence: " + qr.expira.replace("T", " ") + " UTC")
            }
        },
        confirmButton = { TextButton(onClick = cerrar) { Text("Cerrar") } }
    )
}
