package com.proyectointegrador.movil.di

import android.content.Context
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.proyectointegrador.movil.data.local.*
import com.proyectointegrador.movil.data.repository.*
import com.proyectointegrador.movil.network.RetrofitClient
import com.proyectointegrador.movil.ui.viewmodel.*

class Contenedor(context:Context) {
    val base=FoundIADatabase.abrir(context)
    val publicaciones:PublicacionesRepository=ApiPublicacionesRepository(RetrofitClient.api,RoomCachePublica(base))
    val cuenta=CuentaRepository(RetrofitClient.api,publicaciones)
}
object Inyeccion {
    lateinit var contenedor:Contenedor;private set
    fun iniciar(context:Context){if(!::contenedor.isInitialized)contenedor=Contenedor(context.applicationContext)}
}
fun fabrica(objetoId:Long=0,parejaId:String="",tokenRecuperacion:String?=null)=object:ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST") override fun <T:ViewModel> create(modelClass:Class<T>):T {
        val c=Inyeccion.contenedor
        val modelo:ViewModel=when(modelClass) {
            ObjetosViewModel::class.java->ObjetosViewModel(c.publicaciones)
            SesionViewModel::class.java->SesionViewModel(c.cuenta)
            AccesoViewModel::class.java->AccesoViewModel(c.cuenta)
            RegistroViewModel::class.java->RegistroViewModel(c.cuenta)
            VerificacionViewModel::class.java->VerificacionViewModel(c.cuenta)
            PerfilViewModel::class.java->PerfilViewModel(c.cuenta)
            RecuperarViewModel::class.java->RecuperarViewModel(c.cuenta)
            NuevaClaveViewModel::class.java->NuevaClaveViewModel(c.cuenta,tokenRecuperacion)
            PublicarViewModel::class.java->PublicarViewModel(c.cuenta,c.publicaciones)
            PropiosViewModel::class.java->PropiosViewModel(c.cuenta)
            SugerenciasViewModel::class.java->SugerenciasViewModel(c.cuenta,objetoId)
            ParejaViewModel::class.java->ParejaViewModel(c.cuenta,parejaId)
            AvisosViewModel::class.java->AvisosViewModel(c.cuenta)
            PreferenciasViewModel::class.java->PreferenciasViewModel(c.cuenta)
            EliminacionViewModel::class.java->EliminacionViewModel(c.cuenta)
            ReporteViewModel::class.java->ReporteViewModel(c.cuenta,objetoId)
            DetalleAccionesViewModel::class.java->DetalleAccionesViewModel(c.publicaciones,c.cuenta,objetoId)
            else->throw IllegalArgumentException("ViewModel no registrado: ${modelClass.simpleName}")
        }
        return modelo as T
    }
}
