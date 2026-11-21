package com.proyectointegrador.movil

import android.app.Application
import kotlinx.coroutines.launch
import com.proyectointegrador.movil.di.Inyeccion
import com.proyectointegrador.movil.data.session.Sesion

class FoundiaApplication:Application() {
    override fun onCreate() {
        super.onCreate()
        Sesion.iniciar(this)
        Inyeccion.iniciar(this)
        com.proyectointegrador.movil.data.avisos.programarAvisosReclamo(this)
        com.proyectointegrador.movil.data.push.crearCanalesPush(this)
        // H57 y H72: al abrir con sesión guardada se registra el teléfono y se entra a Firebase (si hay proyecto).
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.SupervisorJob()+kotlinx.coroutines.Dispatchers.IO).launch {
            runCatching {Inyeccion.contenedor.cuenta.alAbrirApp()}
        }
    }
}
