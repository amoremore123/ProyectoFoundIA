package com.proyectointegrador.movil.ui.sprint

import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.proyectointegrador.movil.domain.parejaDeEnlace
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.proyectointegrador.movil.di.fabrica
import com.proyectointegrador.movil.ui.viewmodel.*

@Composable fun AvisosScreen(volver:()->Unit,abrir:(String)->Unit,vm:AvisosViewModel=viewModel(factory=fabrica())) {
    val estado by vm.estado.collectAsStateWithLifecycle();val accion by vm.accion.collectAsStateWithLifecycle()
    val evento by vm.evento.collectAsStateWithLifecycle()
    LaunchedEffect(evento){evento?.let {vm.consumirEvento();abrir(it)}}
    Pagina("Mis notificaciones",volver) {
        EstadoAccion(accion)
        Consulta(estado,vm::recargar) { avisos,recargar ->
            Button(onClick=recargar,enabled=!accion.ocupada) {Text("Actualizar avisos")}
            if(avisos.isEmpty()) Text("No tienes notificaciones.")
            avisos.forEach { aviso ->
                Text(aviso.titulo,style=MaterialTheme.typography.titleMedium);Text(aviso.mensaje)
                Text("${aviso.tipo} · ${if(aviso.leida) "Leída" else "Sin leer"}")
                if(parejaDeEnlace(aviso.enlace)!=null) Button(enabled=!accion.ocupada,onClick={vm.abrir(aviso.id)}) {Text("Abrir coincidencia")}
                if(!aviso.leida) OutlinedButton(enabled=!accion.ocupada,onClick={vm.leer(aviso.id)}) {Text("Marcar como leída")}
                HorizontalDivider()
            }
        }
    }
}
