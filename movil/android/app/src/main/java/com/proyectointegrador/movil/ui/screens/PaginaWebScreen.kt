package com.proyectointegrador.movil.ui.screens

import android.annotation.SuppressLint
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.webkit.WebChromeClient
import android.webkit.WebResourceError
import android.webkit.WebResourceRequest
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import com.proyectointegrador.movil.domain.navegacionPermitida
import com.proyectointegrador.movil.ui.components.AvisoEstado
import com.proyectointegrador.movil.ui.components.BotonPrincipal
import com.proyectointegrador.movil.ui.components.Cabecera

/**
 * Desarrollo híbrido con WebView. Seguridad:
 * - solo navega dentro del origen de la web de FoundIA; cualquier otro enlace se abre en el navegador del sistema;
 * - JavaScript solo porque la web es React y es de nuestro propio servidor; sin puente JavaScript hacia la app;
 * - sin acceso a archivos ni a contenido de otras apps, sin ventanas nuevas y sin contenido mixto;
 * - nunca recibe el JWT ni cookies de la sesión de la app.
 */
@SuppressLint("SetJavaScriptEnabled")
@Composable
fun PaginaWebScreen(titulo: String, url: String, base: String, volver: () -> Unit) {
    val contexto = LocalContext.current
    var progreso by remember { mutableIntStateOf(0) }
    var error by remember { mutableStateOf<String?>(null) }
    var vista by remember { mutableStateOf<WebView?>(null) }
    var puedeRetroceder by remember { mutableStateOf(false) }
    BackHandler(enabled = puedeRetroceder) { vista?.goBack() }
    Surface(color = MaterialTheme.colorScheme.background, modifier = Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Column(Modifier.padding(horizontal = 20.dp)) { Cabecera(titulo, volver = volver) }
            if (progreso in 1..99) LinearProgressIndicator(progress = { progreso / 100f }, modifier = Modifier.fillMaxWidth())
            error?.let {
                Column(Modifier.padding(20.dp)) {
                    AvisoEstado(it, true)
                    BotonPrincipal("Reintentar", { error = null; vista?.reload() })
                }
            }
            AndroidView(
                modifier = Modifier.weight(1f).fillMaxWidth(),
                factory = { ctx ->
                    WebView(ctx).apply {
                        settings.javaScriptEnabled = true
                        settings.domStorageEnabled = true
                        settings.allowFileAccess = false
                        settings.allowContentAccess = false
                        settings.javaScriptCanOpenWindowsAutomatically = false
                        settings.setSupportMultipleWindows(false)
                        settings.mixedContentMode = WebSettings.MIXED_CONTENT_NEVER_ALLOW
                        settings.setGeolocationEnabled(false)
                        settings.saveFormData = false
                        webViewClient = object : WebViewClient() {
                            override fun shouldOverrideUrlLoading(view: WebView, request: WebResourceRequest): Boolean {
                                val destino = request.url.toString()
                                if (navegacionPermitida(base, destino)) return false
                                // Un enlace externo no se carga aquí: va al navegador del sistema y solo si es http o https.
                                if (request.url.scheme in listOf("http", "https")) {
                                    runCatching { contexto.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(destino)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
                                }
                                return true
                            }

                            override fun onPageStarted(view: WebView, url: String?, favicon: Bitmap?) { error = null }
                            override fun onPageFinished(view: WebView, url: String?) { puedeRetroceder = view.canGoBack() }
                            override fun onReceivedError(view: WebView, request: WebResourceRequest, falla: WebResourceError) {
                                if (request.isForMainFrame) error = "No se pudo cargar la página. Revisa tu conexión y reintenta."
                            }
                        }
                        webChromeClient = object : WebChromeClient() {
                            override fun onProgressChanged(view: WebView, nuevo: Int) { progreso = nuevo }
                        }
                        loadUrl(url)
                        vista = this
                    }
                },
                onRelease = { it.stopLoading(); it.destroy(); vista = null },
            )
        }
    }
}
