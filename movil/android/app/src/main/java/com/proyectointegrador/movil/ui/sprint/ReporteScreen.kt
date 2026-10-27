package com.proyectointegrador.movil.ui.sprint

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.selection.selectable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.proyectointegrador.movil.ui.components.*
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.proyectointegrador.movil.di.fabrica
import com.proyectointegrador.movil.ui.viewmodel.*

@Composable fun ReporteScreen(objetoId:Long,volver:()->Unit,vm:ReporteViewModel=viewModel(key="reporte-$objetoId",factory=fabrica(objetoId=objetoId))) {
    val estado by vm.estado.collectAsStateWithLifecycle();val accion by vm.accion.collectAsStateWithLifecycle();val f by vm.form.collectAsStateWithLifecycle()
    Pagina("Reportar publicación",volver) {
        if(f.resultado!=null)Text(f.resultado!!)
        else Consulta(estado,vm::recargar) { objeto,_ ->
            Text("Publicación: ${objeto.nombre}");Text("La denuncia será enviada con la identidad de tu sesión. No retira automáticamente la publicación.")
            Panel("¿Qué sucede con esta publicación?") {
            listOf("SPAM" to "Spam","CONTENIDO_INAPROPIADO" to "Contenido inapropiado","INFORMACION_FALSA" to "Información falsa","OTRO" to "Otro motivo").forEach { (clave,texto) ->
                Row(Modifier.selectable(selected=f.motivo==clave,enabled=!accion.ocupada,role=Role.RadioButton,onClick={vm.motivo(clave)})) {
                    RadioButton(selected=f.motivo==clave,onClick=null,enabled=!accion.ocupada);Text(texto)}
            }
            OutlinedTextField(value=f.descripcion,onValueChange=vm::descripcion,label={Text("Explicación (obligatoria para otro motivo)")},enabled=!accion.ocupada)
            }
            EstadoAccion(accion)
            Button(onClick=vm::pedir,enabled=!accion.ocupada && vm.puedeEnviar) {Text("Revisar denuncia")}
            TextButton(onClick=volver,enabled=!accion.ocupada) {Text("Cancelar sin enviar")}
            if(f.confirmar) Confirmacion("¿Confirmar denuncia?","Motivo: ${f.motivo}. ${f.descripcion.trim()}",accion,vm::cancelar,vm::confirmar)
        }
    }
}
