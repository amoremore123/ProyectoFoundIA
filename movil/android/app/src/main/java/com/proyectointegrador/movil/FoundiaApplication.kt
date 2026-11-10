package com.proyectointegrador.movil

import android.app.Application
import com.proyectointegrador.movil.di.Inyeccion
import com.proyectointegrador.movil.data.session.Sesion

class FoundiaApplication:Application() {
    override fun onCreate() {
        super.onCreate()
        Sesion.iniciar(this)
        Inyeccion.iniciar(this)
    }
}
