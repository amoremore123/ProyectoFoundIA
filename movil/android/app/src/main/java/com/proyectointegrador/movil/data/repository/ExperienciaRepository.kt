package com.proyectointegrador.movil.data.repository

import android.content.Context
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext

data class Experiencia(val cargada:Boolean=false,val introduccionCompleta:Boolean=false,val tema:String="sistema")
interface PreferenciasExperiencia {
    suspend fun leer():Experiencia
    suspend fun guardarIntroduccion()
    suspend fun guardarTema(tema:String)
}
/** Solo preferencias de presentación. Nunca contiene datos de cuenta o credenciales. */
class PreferenciasLocales(context:Context):PreferenciasExperiencia {
    private val preferencias=context.applicationContext.getSharedPreferences("foundia-experiencia",Context.MODE_PRIVATE)
    override suspend fun leer()=withContext(Dispatchers.IO) { Experiencia(true,preferencias.getBoolean("introduccion",false),preferencias.getString("tema","sistema")?:"sistema") }
    override suspend fun guardarIntroduccion():Unit=withContext(Dispatchers.IO) { check(preferencias.edit().putBoolean("introduccion",true).commit()) {"No se pudo guardar la bienvenida."} }
    override suspend fun guardarTema(tema:String):Unit=withContext(Dispatchers.IO) { check(preferencias.edit().putString("tema",tema).commit()) {"No se pudo guardar el tema."} }
}
class ExperienciaRepository(private val preferencias:PreferenciasExperiencia) {
    private val _estado=MutableStateFlow(Experiencia());val estado=_estado.asStateFlow()
    suspend fun cargar(){_estado.value=preferencias.leer()}
    suspend fun completar(){preferencias.guardarIntroduccion();_estado.value=_estado.value.copy(introduccionCompleta=true)}
    suspend fun tema(valor:String){require(valor in listOf("sistema","claro","oscuro"));preferencias.guardarTema(valor);_estado.value=_estado.value.copy(tema=valor)}
}
