package com.proyectointegrador.movil.ui.screens.avisos

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.proyectointegrador.movil.ui.components.*
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.proyectointegrador.movil.di.fabrica
import com.proyectointegrador.movil.ui.viewmodel.*
import com.proyectointegrador.movil.domain.*

@Composable fun PreferenciasScreen(volver:()->Unit,vm:PreferenciasViewModel=viewModel(factory=fabrica())) {
    val estado by vm.estado.collectAsStateWithLifecycle();val accion by vm.accion.collectAsStateWithLifecycle();val edicion by vm.edicion.collectAsStateWithLifecycle()
    var avisoPermiso by remember { mutableStateOf<String?>(null) }
    val solicitarAvisos = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { concedido ->
        avisoPermiso = if (concedido) "Notificaciones del sistema habilitadas." else "Puedes seguir leyendo tus avisos dentro de FoundIA."
    }
    Pagina("Preferencias de avisos",volver) {
        if (Build.VERSION.SDK_INT >= 33) {
            OutlinedButton(onClick = { solicitarAvisos.launch(Manifest.permission.POST_NOTIFICATIONS) }) {
                Text("Permitir avisos de reclamo en el dispositivo")
            }
            avisoPermiso?.let { AvisoEstado(it) }
        }
        Consulta(estado,vm::recargarPreferencias) { actuales,_ ->
            val preferencias=edicion?:actuales
            Text("Los cambios afectan a los avisos nuevos; el historial se conserva.")
            Panel("Lo que quieres recibir") {
            listOf("Nuevas coincidencias","Solicitudes de contacto","Reclamos de objetos","Recordatorios de objetos sin resolver").forEachIndexed { indice,texto ->
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                    Text(texto,Modifier.weight(1f))
                    Switch(checked=when(indice){0->preferencias.coincidencias;1->preferencias.contactos;2->preferencias.reclamos;else->preferencias.recordatorios?:true},
                        enabled=!accion.ocupada,onCheckedChange={vm.editar(indice,it)})
                }
            }
            }
            Panel("Por dónde recibirlo") {
                Text("La bandeja de Notificaciones siempre guarda los avisos de los tipos activos. Aquí eliges si además llegan al teléfono o al correo de tu cuenta.",style=MaterialTheme.typography.bodySmall)
                TIPOS_DE_AVISO.forEach {(tipo,texto)->
                    Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween,verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
                        Text(texto,Modifier.weight(1f))
                        SelectorCanal(canalDe(preferencias.canales,tipo),!accion.ocupada,texto) {vm.editarCanal(tipo,it)}
                    }
                }
            }
            EstadoAccion(accion)
            BotonPrincipal("Guardar preferencias",vm::guardar,!accion.ocupada)
            OutlinedButton(onClick=vm::recargarPreferencias,enabled=!accion.ocupada) {Text("Recargar preferencias del servidor")}
            TextButton(onClick=vm::cancelar,enabled=!accion.ocupada) {Text("Cancelar cambios")}
        }
    }
}

@Composable private fun SelectorCanal(canal:String,habilitado:Boolean,tipo:String,cambiar:(String)->Unit) {
    var abierto by remember {mutableStateOf(false)}
    Box {
        OutlinedButton(onClick={abierto=true},enabled=habilitado,modifier=Modifier.semantics{contentDescription="Canal de $tipo: ${etiquetaCanal(canal)}"}) {Text(etiquetaCanal(canal))}
        DropdownMenu(expanded=abierto,onDismissRequest={abierto=false}) {
            CANALES_DE_AVISO.forEach {(valor,texto)->DropdownMenuItem(text={Text(texto)},onClick={abierto=false;cambiar(valor)})}
        }
    }
}
