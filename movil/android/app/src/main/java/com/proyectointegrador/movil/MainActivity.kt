package com.proyectointegrador.movil

import android.os.Bundle
import android.content.Intent
import android.net.Uri
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.proyectointegrador.movil.di.fabrica
import com.proyectointegrador.movil.ui.viewmodel.ExperienciaViewModel
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.proyectointegrador.movil.navigation.AppNavHost
import com.proyectointegrador.movil.ui.theme.FoundIATheme

class MainActivity : ComponentActivity() {
    private var enlace by mutableStateOf<Uri?>(null)
    private var abrirAvisos by mutableStateOf(false)

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        enlace=intent.data
        abrirAvisos = intent.getBooleanExtra("avisosReclamo", false)
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enlace=intent?.data
        abrirAvisos = intent?.getBooleanExtra("avisosReclamo", false) == true
        enableEdgeToEdge()
        setContent {
            val experiencia:ExperienciaViewModel=viewModel(factory=fabrica())
            val ajustes by experiencia.estado.collectAsStateWithLifecycle()
            FoundIATheme(tema=ajustes.tema) {
                AppNavHost(enlace, abrirAvisos, { abrirAvisos = false }) { enlace = null }
            }
        }
    }
}
