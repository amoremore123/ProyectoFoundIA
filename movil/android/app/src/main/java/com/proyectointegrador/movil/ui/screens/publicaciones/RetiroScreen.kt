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
import androidx.compose.runtime.saveable.rememberSaveable
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
import com.proyectointegrador.movil.domain.esCodigoRetiro
import com.proyectointegrador.movil.domain.esEtiquetaCustodia
import com.proyectointegrador.movil.model.QrRetiroPropio
import com.proyectointegrador.movil.ui.components.*
import com.proyectointegrador.movil.ui.viewmodel.RetiroViewModel

@Composable
fun RetiroScreen(volver: () -> Unit, vm: RetiroViewModel = viewModel(factory = fabrica())) {
    val estado by vm.estado.collectAsStateWithLifecycle()
    val accion by vm.accion.collectAsStateWithLifecycle()
    val evento by vm.evento.collectAsStateWithLifecycle()
    // rememberSaveable: lo escaneado no se pierde al rotar el teléfono ni si Android cierra la app en segundo plano.
    var etiqueta by rememberSaveable { mutableStateOf("") }
    var token by rememberSaveable { mutableStateOf("") }
    var escanear by rememberSaveable { mutableStateOf<Boolean?>(null) }
    var solicitado by rememberSaveable { mutableStateOf(false) }
    var confirmar by rememberSaveable { mutableStateOf(false) }
    var aviso by rememberSaveable { mutableStateOf<String?>(null) }
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
            aviso?.let { AvisoEstado(it, true) }
            val etiquetaOk = esEtiquetaCustodia(etiqueta)
            val tokenOk = esCodigoRetiro(token)
            OutlinedTextField(etiqueta, { etiqueta = it.take(500); aviso = null; vm.descartarAviso() }, label = { Text("Etiqueta de custodia") },
                enabled = !accion.ocupada, modifier = Modifier.fillMaxWidth(), visualTransformation = PasswordVisualTransformation(),
                isError = etiqueta.isNotBlank() && !etiquetaOk,
                supportingText = { if (etiqueta.isNotBlank()) Text(if (etiquetaOk) "Etiqueta de custodia leída." else "Este valor no parece una etiqueta de custodia.") })
            OutlinedButton(onClick = { iniciar(true) }, enabled = !accion.ocupada) { Text("Escanear etiqueta") }
            val abrirWeb = LocalAbrirWeb.current
            if (etiquetaOk && abrirWeb != null) TextButton(onClick = { abrirWeb("etiqueta", etiqueta) }) { Text("Ver la etiqueta en la web de FoundIA") }
            OutlinedTextField(token, { token = it.take(64); aviso = null; vm.descartarAviso() }, label = { Text("Código de retiro") },
                enabled = !accion.ocupada, modifier = Modifier.fillMaxWidth(), visualTransformation = PasswordVisualTransformation(),
                isError = token.isNotBlank() && !tokenOk,
                supportingText = { if (token.isNotBlank()) Text(if (tokenOk) "Código de retiro leído." else "Este valor no parece un código de retiro.") })
            OutlinedButton(onClick = { iniciar(false) }, enabled = !accion.ocupada) { Text("Escanear QR de retiro") }
            EstadoAccion(accion)
            BotonPrincipal("Revisar entrega", { confirmar = true }, etiquetaOk && tokenOk && !accion.ocupada)
        }
    }
    escanear?.let { esEtiqueta ->
        EscanerCustodia(cerrar = { escanear = null }, detectado = { valor ->
            // El contenido decide a qué campo corresponde: un QR de retiro cabe en el campo de la etiqueta por su
            // largo, pero no es una etiqueta de custodia.
            when {
                esEtiqueta && esEtiquetaCustodia(valor) -> { etiqueta = valor.trim(); aviso = null; vm.descartarAviso() }
                !esEtiqueta && esCodigoRetiro(valor) -> { token = valor.trim(); aviso = null; vm.descartarAviso() }
                esEtiqueta && esCodigoRetiro(valor) -> aviso = "Ese QR es un código de retiro. Para este campo escanea la etiqueta pegada en el objeto."
                !esEtiqueta && esEtiquetaCustodia(valor) -> aviso = "Ese QR es la etiqueta del objeto. Para este campo escanea el QR de retiro que presenta la persona."
                else -> aviso = "Ese QR no pertenece a FoundIA o no corresponde al campo seleccionado."
            }
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
