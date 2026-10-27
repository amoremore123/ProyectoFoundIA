package com.proyectointegrador.movil.ui.components

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import com.proyectointegrador.movil.model.Objeto
import com.proyectointegrador.movil.domain.urlImagen

@Composable fun TipoChip(tipo:String,modifier:Modifier=Modifier) {
 val perdido=tipo=="PERDIDO"
 Surface(color=if(perdido)MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.secondaryContainer,shape=RoundedCornerShape(8.dp),modifier=modifier) {
  Text(if(perdido)"Perdido" else "Encontrado",Modifier.padding(horizontal=10.dp,vertical=5.dp),color=if(perdido)MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSecondaryContainer,style=MaterialTheme.typography.labelSmall)
 }
}
@Composable fun FotoObjeto(objeto:Objeto,modifier:Modifier=Modifier) {
 val url=urlImagen(objeto.fotosSeguras.firstOrNull()?.url)
 Box(modifier.background(MaterialTheme.colorScheme.surfaceVariant),contentAlignment=Alignment.Center) {
  if(url!=null)SubcomposeAsyncImage(model=url,contentDescription="Foto de ${objeto.nombre}",modifier=Modifier.fillMaxSize(),contentScale=ContentScale.Crop,
   loading={Box(Modifier.fillMaxSize(),contentAlignment=Alignment.Center){CircularProgressIndicator(Modifier.size(24.dp),strokeWidth=2.dp)}},
   error={SinFoto("Foto no disponible")})
  else SinFoto("Sin fotografía")
 }
}
@Composable private fun SinFoto(texto:String){Column(Modifier.fillMaxSize(),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.Center){Icon(Simbolos.Foto,null,Modifier.size(28.dp),tint=MaterialTheme.colorScheme.onSurfaceVariant);Spacer(Modifier.height(6.dp));Text(texto,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}}
@Composable fun ObjetoCard(objeto:Objeto,onClick:()->Unit,modifier:Modifier=Modifier,compacto:Boolean=false) {
 Card(onClick=onClick,modifier=modifier.fillMaxWidth(),shape=RoundedCornerShape(22.dp),colors=CardDefaults.cardColors(containerColor=MaterialTheme.colorScheme.surface),border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant)) {
  if(compacto)Row(Modifier.padding(10.dp),horizontalArrangement=Arrangement.spacedBy(14.dp),verticalAlignment=Alignment.CenterVertically){
   FotoObjeto(objeto,Modifier.size(96.dp).clip(RoundedCornerShape(16.dp)))
   Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(6.dp)){TipoChip(objeto.tipoSeguro);Text(objeto.nombre,style=MaterialTheme.typography.titleMedium,maxLines=2,overflow=TextOverflow.Ellipsis);FilaDato(Simbolos.Lugar,objeto.ubicacion?:"Sin ubicación")}
  } else Column {
   Box {FotoObjeto(objeto,Modifier.fillMaxWidth().height(180.dp));TipoChip(objeto.tipoSeguro,Modifier.padding(12.dp))}
   Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
    Row(verticalAlignment=Alignment.CenterVertically){Text(objeto.nombre,Modifier.weight(1f),style=MaterialTheme.typography.titleMedium,maxLines=2,overflow=TextOverflow.Ellipsis);Icon(Simbolos.Flecha,"Ver detalles",Modifier.size(19.dp),tint=MaterialTheme.colorScheme.primary)}
    FilaDato(Simbolos.Lugar,objeto.ubicacion?:"Sin ubicación")
    FilaDato(Simbolos.Fecha,objeto.fechaObjeto?:"Sin fecha")
   }
  }
 }
}
