package com.proyectointegrador.movil

import android.os.Bundle
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.proyectointegrador.movil.navigation.AppNavHost
import com.proyectointegrador.movil.ui.theme.FoundIATheme

class MainActivity : ComponentActivity() {
    private var enlace by mutableStateOf<Uri?>(null)
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        enlace=intent.data
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enlace=intent?.data
        com.proyectointegrador.movil.network.Sesion.iniciar(applicationContext)
        enableEdgeToEdge()
        setContent {
            FoundIATheme {
                AppNavHost(enlace) {enlace=null}
            }
        }
    }
}
