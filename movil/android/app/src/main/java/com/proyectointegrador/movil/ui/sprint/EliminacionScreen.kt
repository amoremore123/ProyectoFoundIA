package com.proyectointegrador.movil.ui.sprint

import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.proyectointegrador.movil.di.fabrica
import com.proyectointegrador.movil.ui.viewmodel.*

@Composable fun EliminacionScreen(volver:()->Unit,vm:EliminacionViewModel=viewModel(factory=fabrica())) {
    val estado by vm.estado.collectAsStateWithLifecycle();val accion by vm.accion.collectAsStateWithLifecycle();val confirmar by vm.confirmacion.collectAsStateWithLifecycle()
    Pagina("Solicitud de eliminación",volver) {
        Consulta(estado,vm::recargar) { solicitud,recargar ->
            Text(solicitud.informacion);Text("Estado: ${if(solicitud.estado=="SIN_SOLICITUD") "Sin solicitud" else solicitud.estado}")
            solicitud.fechaSolicitud?.let {Text("Fecha registrada: $it")}
            EstadoAccion(accion)
            if(solicitud.estado=="SIN_SOLICITUD") OutlinedButton(onClick=vm::pedir,enabled=!accion.ocupada) {Text("Solicitar eliminación de mi cuenta y datos")}
            Button(onClick=recargar,enabled=!accion.ocupada) {Text("Consultar estado actualizado")}
            if(confirmar) Confirmacion("¿Enviar la solicitud?","Se registrará un trámite pendiente de revisión. Tu cuenta y tus datos no se borran con esta acción.",accion,vm::cancelar,vm::confirmar)
        }
    }
}
