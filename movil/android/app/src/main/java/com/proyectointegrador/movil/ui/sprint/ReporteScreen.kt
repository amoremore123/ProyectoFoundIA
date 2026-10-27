package com.proyectointegrador.movil.ui.sprint

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import com.proyectointegrador.movil.model.Denuncia
import com.proyectointegrador.movil.network.*

@Composable fun ReporteScreen(objetoId:Long,volver:()->Unit) {
    val token by Sesion.token.collectAsState()
    var resultado by remember(objetoId,token) {mutableStateOf<String?>(null)}
    Pagina("Reportar publicación",volver) {
        if(resultado!=null) Text(resultado!!)
        else Consulta("$objetoId:$token",{RetrofitClient.api.objetoPorId(objetoId)}) { objeto,_ ->
            Text("Publicación: ${objeto.nombre}")
            Text("La denuncia será enviada con la identidad de tu sesión. No retira automáticamente la publicación.")
            var motivo by remember {mutableStateOf("SPAM")};var descripcion by remember {mutableStateOf("")}
            var confirmar by remember {mutableStateOf(false)}
            val accion=recordarAccion()
            listOf("SPAM" to "Spam", "CONTENIDO_INAPROPIADO" to "Contenido inapropiado",
                "INFORMACION_FALSA" to "Información falsa", "OTRO" to "Otro motivo").forEach { (clave,texto) ->
                Row {RadioButton(selected=motivo==clave,onClick={motivo=clave},enabled=!accion.ocupada);Text(texto)}
            }
            OutlinedTextField(value=descripcion,onValueChange={descripcion=it.take(2000)},label={Text("Explicación (obligatoria para otro motivo)")},enabled=!accion.ocupada)
            EstadoAccion(accion)
            Button(onClick={confirmar=true},enabled=!accion.ocupada && (motivo!="OTRO" || descripcion.isNotBlank())) {Text("Revisar denuncia")}
            TextButton(onClick=volver,enabled=!accion.ocupada) {Text("Cancelar sin enviar")}
            if(confirmar) Confirmacion("¿Confirmar denuncia?", "Motivo: $motivo. ${descripcion.trim()}",accion,{confirmar=false},{accion.ejecutar {
                val respuesta=RetrofitClient.api.reportar(objetoId,Denuncia(motivo,descripcion.trim().ifEmpty {null}))
                resultado=if(respuesta.repetido) "Ya existe tu denuncia para esta publicación. Se conserva el trámite original."
                    else "Denuncia registrada con número ${respuesta.id}. Estado: ${respuesta.estado}."
                confirmar=false
            }})
        }
    }
}
