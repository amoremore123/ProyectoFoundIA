package com.proyectointegrador.movil.ui.screens.avisos
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.unit.dp
import com.proyectointegrador.movil.domain.rutaDeAviso
import com.proyectointegrador.movil.model.FiltroAvisos
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
  val filtro by vm.filtro.collectAsStateWithLifecycle()
  Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement=Arrangement.spacedBy(8.dp)) {
   listOf(FiltroAvisos.TODAS to "Todas", FiltroAvisos.NO_LEIDAS to "Sin leer", FiltroAvisos.LEIDAS to "Leídas").forEach { (opcion, titulo) ->
    FilterChip(selected=filtro==opcion, onClick={vm.filtrar(opcion)}, enabled=!accion.ocupada, label={Text(titulo)})
   }
  }
  if(!estado.cargando && estado.datos==null) OutlinedButton(onClick=vm::recargar, enabled=!accion.ocupada){Text("Reintentar")}
  Consulta(estado,vm::recargar){historial,recargar->
   val avisos=historial.contenido
   Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text("${historial.noLeidas} sin leer",Modifier.weight(1f),style=MaterialTheme.typography.titleMedium);TextButton(onClick=recargar,enabled=!accion.ocupada){Text("Actualizar")}}
   if(historial.noLeidas>0) OutlinedButton(onClick=vm::leerTodos, enabled=!accion.ocupada){Text("Marcar todas como leídas")}
   if(avisos.isEmpty())Vacio("Todo al día","Cuando haya novedades de tus objetos, las encontrarás aquí.",Simbolos.Avisos)
   avisos.forEach {aviso->Surface(color=if(aviso.leida)MaterialTheme.colorScheme.surface else MaterialTheme.colorScheme.primaryContainer,shape=RoundedCornerShape(22.dp),border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant),modifier=Modifier.fillMaxWidth()) {
    Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
     Row(horizontalArrangement=Arrangement.spacedBy(12.dp),verticalAlignment=Alignment.CenterVertically){Icon(if(aviso.tipo.contains("COINCIDENCIA"))Simbolos.Chispa else if(aviso.tipo.contains("CONTACTO"))Simbolos.Correo else Simbolos.Avisos,null,tint=MaterialTheme.colorScheme.primary);Text(aviso.titulo,Modifier.weight(1f),style=MaterialTheme.typography.titleMedium);if(!aviso.leida)Badge()}
     Text(aviso.mensaje,style=MaterialTheme.typography.bodyMedium)
     Text(listOfNotNull(aviso.fechaCreacion?.replace("T"," ")?.take(16),if(aviso.leida)"Leída" else "Sin leer").joinToString(" · "),style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)
     if(rutaDeAviso(aviso.enlace, aviso.tipo)!=null)TextButton(enabled=!accion.ocupada,onClick={vm.abrir(aviso.id)}){Text(if(aviso.tipo=="RECORDATORIO_OBJETO")"Ver publicación" else "Ver detalle");Spacer(Modifier.width(8.dp));Icon(Simbolos.Flecha,null,Modifier.size(16.dp))}
     if(!aviso.leida)OutlinedButton(enabled=!accion.ocupada,onClick={vm.leer(aviso.id)}){Text("Marcar como leída")}
    }
   }}
   if(historial.pagina+1<historial.totalPaginas) OutlinedButton(onClick=vm::verMas, enabled=!accion.ocupada){Text("Ver más avisos")}
  }
 }
}
