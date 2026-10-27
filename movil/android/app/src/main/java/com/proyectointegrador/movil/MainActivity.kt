package com.proyectointegrador.movil

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import com.proyectointegrador.movil.navigation.AppNavHost
import com.proyectointegrador.movil.ui.theme.FoundIATheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        com.proyectointegrador.movil.network.Sesion.iniciar(applicationContext)
        enableEdgeToEdge()
        setContent {
            FoundIATheme {
                AppNavHost()
            }
        }
    }
}
