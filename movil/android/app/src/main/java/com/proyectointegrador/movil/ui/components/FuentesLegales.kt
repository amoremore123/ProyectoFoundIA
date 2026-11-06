package com.proyectointegrador.movil.ui.components

import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalUriHandler
import com.proyectointegrador.movil.model.Documentos
import java.net.URI

fun fuenteOficialTecsup(url: String): Boolean = runCatching {
    val uri = URI(url)
    uri.scheme == "https" && uri.host == "www.tecsup.edu.pe" && uri.userInfo == null && uri.port == -1
}.getOrDefault(false)

fun avisoDocumentos(documentos: Documentos): String = documentos.aviso?.takeIf { it.isNotBlank() }
    ?: if (documentos.estado.contains("PENDIENTE"))
        "Documentos del proyecto pendientes de revisión institucional. Versión ${documentos.version}."
    else "Documentos de FoundIA. Versión ${documentos.version}."

@Composable fun FuentesLegales(documentos: Documentos) {
    val fuentes = documentos.fuentes.orEmpty().filter { fuenteOficialTecsup(it.url) }
    val navegador = LocalUriHandler.current
    var error by remember { mutableStateOf(false) }
    if (fuentes.isNotEmpty()) {
        Text("Fuentes oficiales de Tecsup")
        Text("Referencia institucional. Los textos completos están incluidos en los documentos de FoundIA.")
        fuentes.forEach { fuente ->
            TextButton(onClick = {
                error = runCatching { navegador.openUri(fuente.url) }.isFailure
            }) { Text("${fuente.titulo} (sitio externo)") }
            Text("Consulta: ${fuente.consultadoEl}")
        }
        Text("Leer estas fuentes no marca la casilla ni autoriza publicidad.")
        if (error) AvisoEstado("No se pudo abrir el navegador. Puedes leer la transcripción incluida aquí.")
    }
}
