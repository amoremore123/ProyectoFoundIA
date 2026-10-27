package com.proyectointegrador.movil.ui.sprint

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.proyectointegrador.movil.di.fabrica
import com.proyectointegrador.movil.ui.viewmodel.*

@Composable fun PreferenciasScreen(volver:()->Unit,vm:PreferenciasViewModel=viewModel(factory=fabrica())) {
    val estado by vm.estado.collectAsStateWithLifecycle();val accion by vm.accion.collectAsStateWithLifecycle();val edicion by vm.edicion.collectAsStateWithLifecycle()
    Pagina("Preferencias de avisos",volver) {
        Consulta(estado,vm::recargarPreferencias) { actuales,_ ->
            val preferencias=edicion?:actuales
            Text("Los cambios afectan a los avisos nuevos; el historial se conserva.")
            listOf("Nuevas coincidencias","Solicitudes de contacto","Reclamos de objetos").forEachIndexed { indice,texto ->
                Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
                    Text(texto,Modifier.weight(1f))
                    Switch(checked=when(indice){0->preferencias.coincidencias;1->preferencias.contactos;else->preferencias.reclamos},
                        enabled=!accion.ocupada,onCheckedChange={vm.editar(indice,it)})
                }
            }
            EstadoAccion(accion)
            Button(enabled=!accion.ocupada,onClick=vm::guardar) {Text("Guardar preferencias")}
            OutlinedButton(onClick=vm::recargarPreferencias,enabled=!accion.ocupada) {Text("Recargar preferencias del servidor")}
            TextButton(onClick=vm::cancelar,enabled=!accion.ocupada) {Text("Cancelar cambios")}
        }
    }
}
