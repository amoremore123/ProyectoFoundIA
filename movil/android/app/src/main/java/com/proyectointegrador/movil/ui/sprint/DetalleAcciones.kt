package com.proyectointegrador.movil.ui.sprint

import android.content.Intent
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.proyectointegrador.movil.model.Objeto
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.proyectointegrador.movil.di.fabrica
import com.proyectointegrador.movil.ui.viewmodel.*

@Composable fun DetalleAcciones(objeto:Objeto,reportar:(Long)->Unit,entrar:()->Unit,
    vm:DetalleAccionesViewModel=viewModel(key="acciones-${objeto.id}",factory=fabrica(objetoId=objeto.id))) {
    val contexto=LocalContext.current
    val sesion by vm.sesion.collectAsStateWithLifecycle();val accion by vm.accion.collectAsStateWithLifecycle();val enlace by vm.evento.collectAsStateWithLifecycle()
    LaunchedEffect(enlace){enlace?.let { url ->
        vm.consumirEvento()
        try {contexto.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply {type="text/plain";putExtra(Intent.EXTRA_TEXT,url)},"Compartir publicación de FoundIA"))}
        catch(_:android.content.ActivityNotFoundException){vm.errorCompartir()}
    }}
    if(sesion.autenticado)OutlinedButton(onClick={reportar(objeto.id)}) {Text("Reportar esta publicación")}
    else TextButton(onClick=entrar) {Text("Inicia sesión para reportar")}
    if(objeto.esPerdido) {
        Button(enabled=!accion.ocupada,onClick=vm::compartir) {Text("Compartir publicación")}
        Text("Se abrirá el selector del sistema. Puedes elegir una aplicación o cancelar; FoundIA no confirma el envío.")
    }
    EstadoAccion(accion)
}
