package com.proyectointegrador.movil.ui.sprint

import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.proyectointegrador.movil.model.ConfirmarEliminacion
import com.proyectointegrador.movil.network.*

@Composable fun EliminacionScreen(volver:()->Unit) {
    val token by Sesion.token.collectAsState()
    Pagina("Solicitud de eliminación",volver) {
        Consulta(token,{RetrofitClient.api.eliminacion()}) { estado,recargar ->
            var confirmar by remember {mutableStateOf(false)}
            val accion=recordarAccion()
            Text(estado.informacion)
            Text("Estado: ${if(estado.estado=="SIN_SOLICITUD") "Sin solicitud" else estado.estado}")
            estado.fechaSolicitud?.let {Text("Fecha registrada: $it")}
            EstadoAccion(accion)
            if(estado.estado=="SIN_SOLICITUD") OutlinedButton(onClick={confirmar=true},enabled=!accion.ocupada) {Text("Solicitar eliminación de mi cuenta y datos")}
            Button(onClick=recargar,enabled=!accion.ocupada) {Text("Consultar estado actualizado")}
            if(confirmar) Confirmacion("¿Enviar la solicitud?", "Se registrará un trámite pendiente de revisión. Tu cuenta y tus datos no se borran con esta acción.",
                accion,{confirmar=false},{accion.ejecutar {
                    RetrofitClient.api.solicitarEliminacion(ConfirmarEliminacion());confirmar=false;recargar()
                }})
        }
    }
}
