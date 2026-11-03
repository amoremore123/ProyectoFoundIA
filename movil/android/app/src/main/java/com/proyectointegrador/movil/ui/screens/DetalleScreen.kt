package com.proyectointegrador.movil.ui.screens
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.proyectointegrador.movil.di.fabrica
import com.proyectointegrador.movil.ui.components.*
import com.proyectointegrador.movil.ui.viewmodel.ObjetosViewModel

@Composable fun DetalleScreen(objetoId:Long,onBack:()->Unit,onObjetoClick:(Long)->Unit,onComparar:(String)->Unit={},onReportar:(Long)->Unit={},onAcceso:()->Unit={},viewModel:ObjetosViewModel=viewModel(factory=fabrica())) {
 val objeto by viewModel.detalle.collectAsStateWithLifecycle();val coincidencias by viewModel.coincidencias.collectAsStateWithLifecycle();val cargando by viewModel.cargandoDetalle.collectAsStateWithLifecycle();val error by viewModel.errorDetalle.collectAsStateWithLifecycle();val aviso by viewModel.avisoDetalle.collectAsStateWithLifecycle()
 LaunchedEffect(objetoId){viewModel.cargarDetalle(objetoId)}
 Surface(color=MaterialTheme.colorScheme.background,modifier=Modifier.fillMaxSize()) {
  LazyColumn(Modifier.statusBarsPadding(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(18.dp)) {
   item{Cabecera("Publicación",volver=onBack)}
   when {
    cargando->item{Esqueleto()}
    error!=null->item{AvisoEstado(error!!,true);BotonPrincipal("Reintentar",{viewModel.cargarDetalle(objetoId)})}
    objeto!=null->{val o=objeto!!
     item{if(o.fotosSeguras.size>1)LazyRow(horizontalArrangement=Arrangement.spacedBy(12.dp)){items(o.fotosSeguras,key={it.id}){foto->FotoObjeto(o.copy(fotos=listOf(foto)),Modifier.width(280.dp).height(260.dp).clip(RoundedCornerShape(26.dp)))}} else FotoObjeto(o,Modifier.fillMaxWidth().height(260.dp).clip(RoundedCornerShape(26.dp)))}
     item{Row(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically){TipoChip(o.tipoSeguro);Text(o.estado?:"Sin estado",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.onSurfaceVariant)};Spacer(Modifier.height(10.dp));Text(o.nombre,style=MaterialTheme.typography.headlineLarge);o.categoria?.nombre?.let {Text(it,style=MaterialTheme.typography.titleSmall,color=MaterialTheme.colorScheme.primary)}}
     aviso?.let {item{AvisoEstado(it);TextButton(onClick={viewModel.cargarDetalle(objetoId)}){Text("Actualizar publicación")}}}
     item{Panel {FilaDato(Simbolos.Lugar,o.ubicacion?:"Sin ubicación");HorizontalDivider(color=MaterialTheme.colorScheme.outlineVariant);FilaDato(Simbolos.Fecha,o.fechaObjeto?:"Sin fecha");FilaDato(Simbolos.Perfil,o.publicadoPor?.nombreCompleto?:"Comunidad Tecsup")}}
     item{Text("Los detalles importan",style=MaterialTheme.typography.titleLarge);Spacer(Modifier.height(10.dp));Text(o.descripcion?.ifBlank {"Sin descripción"}?:"Sin descripción",style=MaterialTheme.typography.bodyLarge)}
     item{Panel{com.proyectointegrador.movil.ui.sprint.DetalleAcciones(o,onReportar,onAcceso)}}
     if(coincidencias.isNotEmpty()) {
      item{Text("Posibles coincidencias",style=MaterialTheme.typography.titleLarge);Text("Sugerencias reales según las características del objeto.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
      items(coincidencias,key={it.objetoId}){c->Surface(onClick={onComparar(c.coincidenciaId?:if(o.esPerdido)"${o.id}-${c.objetoId}" else "${c.objetoId}-${o.id}")},shape=RoundedCornerShape(20.dp),color=MaterialTheme.colorScheme.tertiaryContainer){Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){Text(c.nombre?:"Objeto",style=MaterialTheme.typography.titleMedium);Text("${c.porcentaje?.let {"%.0f".format(it)}?:"—"}% de similitud · ${c.nivel?:"Sin nivel"}",color=MaterialTheme.colorScheme.onTertiaryContainer);FilaDato(Simbolos.Lugar,c.ubicacion?:"Sin ubicación");Text("Comparar objetos →",style=MaterialTheme.typography.labelLarge)}}}
     }else item{Text("Aún no hay coincidencias sugeridas.",color=MaterialTheme.colorScheme.onSurfaceVariant,style=MaterialTheme.typography.bodySmall)}
    }
   }
  }
 }
}
