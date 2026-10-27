package com.proyectointegrador.movil

import android.app.Application
import com.proyectointegrador.movil.di.Inyeccion
import com.proyectointegrador.movil.network.Sesion
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

class FoundiaApplication:Application() {
    private val scope=CoroutineScope(SupervisorJob()+Dispatchers.IO)
    override fun onCreate() {
        super.onCreate()
        Sesion.iniciar(this)
        Inyeccion.iniciar(this)
        // Una sesión que cambia no debe dejar consultas de otra cuenta en el almacenamiento local.
        scope.launch { Inyeccion.contenedor.cuenta.cambiosSesion.drop(1).collect {Inyeccion.contenedor.publicaciones.invalidar()} }
    }
}
