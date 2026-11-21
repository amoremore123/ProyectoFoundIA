package com.proyectointegrador.movil.ui.screens.publicaciones

import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.proyectointegrador.movil.di.fabrica
import com.proyectointegrador.movil.ui.components.*
import com.proyectointegrador.movil.ui.viewmodel.ReclamoCustodiaViewModel

@Composable
fun ReclamoCustodiaScreen(id: Long, volver: () -> Unit,
    vm: ReclamoCustodiaViewModel = viewModel(key = "reclamo-$id", factory = fabrica(objetoId = id))) {
    val estado by vm.estado.collectAsStateWithLifecycle()
    val accion by vm.accion.collectAsStateWithLifecycle()
    val qr by vm.qr.collectAsStateWithLifecycle()
    qr?.let { MostrarQrRetiro(it, vm::ocultarQr) }
    var respuesta by remember { mutableStateOf("") }
    var confirmar by remember { mutableStateOf(false) }

    Pagina("Reclamo de custodia", volver) {
        EstadoAccion(accion)
        Consulta(estado, vm::recargar) { reclamo, _ ->
            if (reclamo.estado == "SIN_RECLAMO") {
                Text("¿Qué detalle del objeto solo tú podrías reconocer?")
                Text("La biblioteca revisará tu respuesta. Evita incluir contraseñas, documentos o teléfonos.")
                OutlinedTextField(
                    value = respuesta, onValueChange = { respuesta = it.take(500) },
                    label = { Text("Respuesta de verificación") }, modifier = Modifier.fillMaxWidth(),
                    enabled = !accion.ocupada, minLines = 3
                )
                BotonPrincipal("Revisar reclamo", { confirmar = true }, respuesta.trim().length >= 3 && !accion.ocupada)
            } else {
                val mensaje = when (reclamo.estado) {
                    "ACEPTADO" -> "Aceptado por la biblioteca"
                    "RECHAZADO" -> "Rechazado por la biblioteca"
                    "RETIRADO" -> "Objeto retirado"
                    else -> "Pendiente de revisión"
                }
                Panel("Estado de tu reclamo") { Text(mensaje) }
                if (reclamo.estado == "ACEPTADO") OutlinedButton(onClick = vm::verQr, enabled = !accion.ocupada) {
                    Text("Mostrar QR de retiro")
                }
                reclamo.id?.let { com.proyectointegrador.movil.ui.screens.chat.BotonConversar(reclamoId = it, texto = "Escribir a la biblioteca") }
                TextButton(onClick = vm::recargar, enabled = !accion.ocupada) { Text("Actualizar") }
            }
        }
    }
    if (confirmar) Confirmacion(
        "Enviar a la biblioteca", "La biblioteca recibirá el detalle de verificación.",
        accion, { confirmar = false }, {
            confirmar = false
            vm.reclamar(respuesta)
        }
    )
}
