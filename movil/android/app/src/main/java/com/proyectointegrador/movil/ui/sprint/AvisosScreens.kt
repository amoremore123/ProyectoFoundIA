package com.proyectointegrador.movil.ui.sprint
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.unit.dp
import com.proyectointegrador.movil.domain.parejaDeEnlace
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.proyectointegrador.movil.di.fabrica
import com.proyectointegrador.movil.ui.viewmodel.*
import com.proyectointegrador.movil.ui.components.*

@Composable fun AvisosScreen(volver:()->Unit,abrir:(String)->Unit,vm:AvisosViewModel=viewModel(factory=fabrica())) {
 val estado by vm.estado.collectAsStateWithLifecycle();val accion by vm.accion.collectAsStateWithLifecycle();val evento by vm.evento.collectAsStateWithLifecycle()
 LaunchedEffect(evento){evento?.let {vm.consumirEvento();abrir(it)}}
 Pagina("Tus novedades",volver) {
  Text("Cada aviso puede acercarte a lo que buscas.",color=MaterialTheme.colorScheme.onSurfaceVariant)
  EstadoAccion(accion)
  Consulta(estado,vm::recargar){avisos,recargar->
   Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text("${avisos.count {!it.leida}} sin leer",Modifier.weight(1f),style=MaterialTheme.typography.titleMedium);TextButton(onClick=recargar,enabled=!accion.ocupada){Text("Actualizar")}}
   if(avisos.isEmpty())Vacio("Todo al día","Cuando haya novedades de tus objetos, las encontrarás aquí.",Simbolos.Avisos)
   avisos.forEach {aviso->Surface(color=if(aviso.leida)MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.primaryContainer,shape=RoundedCornerShape(22.dp),border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant),modifier=Modifier.fillMaxWidth()) {
    Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
     Row(horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically){Icon(if(aviso.tipo.contains("COINCIDENCIA"))Simbolos.Chispa else if(aviso.tipo.contains("CONTACTO"))Simbolos.Correo else Simbolos.Avisos,null,tint=MaterialTheme.colorScheme.primary);Text(aviso.titulo,Modifier.weight(1f),style=MaterialTheme.typography.titleMedium);if(!aviso.leida)Badge()}
     Text(aviso.mensaje,style=MaterialTheme.typography.bodyMedium)
     Text(listOfNotNull(aviso.fechaCreacion?.replace("T"," ")?.take(16),if(aviso.leida)"Leída" else "Sin leer").joinToString(" · "),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
     if(parejaDeEnlace(aviso.enlace)!=null)TextButton(enabled=!accion.ocupada,onClick={vm.abrir(aviso.id)}){Text("Ver coincidencia");Spacer(Modifier.width(8.dp));Icon(Simbolos.Flecha,null,Modifier.size(16.dp))}
     if(!aviso.leida)OutlinedButton(enabled=!accion.ocupada,onClick={vm.leer(aviso.id)}){Text("Marcar como leída")}
    }
   }}
  }
 }
}
