package com.proyectointegrador.movil.ui.sprint

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.proyectointegrador.movil.model.*
import com.proyectointegrador.movil.network.*

/** Solo admite rutas públicas propias; nunca abre URLs arbitrarias de una respuesta. */
fun parejaDeEnlace(enlace:String?):String? = enlace?.let { Regex("^/coincidencias/([1-9][0-9]*-[1-9][0-9]*)$").matchEntire(it)?.groupValues?.get(1) }

@Composable fun AvisosScreen(volver:()->Unit,abrir:(String)->Unit) {
    val token by Sesion.token.collectAsState()
    val accion=recordarAccion()
    Pagina("Mis notificaciones",volver) {
        EstadoAccion(accion)
        Consulta(token,{RetrofitClient.api.avisos()}) { avisos,recargar ->
            Button(onClick=recargar,enabled=!accion.ocupada) {Text("Actualizar avisos")}
            if(avisos.isEmpty()) Text("No tienes notificaciones.")
            avisos.forEach { aviso ->
                Text(aviso.titulo,style=MaterialTheme.typography.titleMedium)
                Text(aviso.mensaje);Text("${aviso.tipo} · ${if(aviso.leida) "Leída" else "Sin leer"}")
                val pareja=parejaDeEnlace(aviso.enlace)
                if(pareja!=null) Button(enabled=!accion.ocupada,onClick={accion.ejecutar {
                    if(!aviso.leida) RetrofitClient.api.leer(aviso.id)
                    abrir(pareja)
                }}) {Text("Abrir coincidencia")}
                if(!aviso.leida) OutlinedButton(enabled=!accion.ocupada,onClick={accion.ejecutar {
                    RetrofitClient.api.leer(aviso.id);recargar()
                }}) {Text("Marcar como leída")}
                HorizontalDivider()
            }
        }
    }
}
