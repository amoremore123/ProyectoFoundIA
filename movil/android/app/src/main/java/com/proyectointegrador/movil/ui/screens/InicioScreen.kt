package com.proyectointegrador.movil.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.proyectointegrador.movil.R
import com.proyectointegrador.movil.di.fabrica
import com.proyectointegrador.movil.ui.components.*
import com.proyectointegrador.movil.ui.viewmodel.*

@Composable fun InicioScreen(onObjetoClick:(Long)->Unit,abrir:(String)->Unit={},comunidad:ComunidadEstado=ComunidadEstado(),viewModel:ObjetosViewModel=viewModel(factory=fabrica())) {
 val state by viewModel.state.collectAsStateWithLifecycle();val categorias by viewModel.categorias.collectAsStateWithLifecycle();val filtro by viewModel.busqueda.collectAsStateWithLifecycle();val errorCategorias by viewModel.errorCategorias.collectAsStateWithLifecycle()
 LaunchedEffect(Unit){viewModel.cargarRecientes();viewModel.cargarCategorias()}
 Surface(color=MaterialTheme.colorScheme.background,modifier=Modifier.fillMaxSize()) {
  LazyColumn(Modifier.statusBarsPadding(),contentPadding=PaddingValues(start=20.dp,end=20.dp,top=16.dp,bottom=24.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
   item {Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){
    Column(Modifier.weight(1f)){LogoFoundia();Spacer(Modifier.height(12.dp));Text(comunidad.usuario?.let {"Hola, ${it.nombre.split(" ").first()}"}?:"Hola, comunidad",style=MaterialTheme.typography.titleLarge);Text("Juntos, nada se pierde.",style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
    IconButton(onClick={abrir("avisos")}){BadgedBox(badge={if(comunidad.sinLeer>0)Badge{Text(comunidad.sinLeer.toString())}}){Icon(Simbolos.Avisos,"Notificaciones")}}
    Surface(onClick={abrir("perfil")},shape=CircleShape,color=MaterialTheme.colorScheme.primaryContainer,modifier=Modifier.size(42.dp)){Box(contentAlignment=Alignment.Center){if(comunidad.usuario!=null)Text(comunidad.usuario.nombre.take(1).uppercase(),style=MaterialTheme.typography.titleMedium) else Icon(Simbolos.Perfil,"Mi perfil",Modifier.padding(10.dp))}}
   }}
   item {Box(Modifier.fillMaxWidth().clip(RoundedCornerShape(26.dp)).background(AzulNoche)) {
    Image(painterResource(R.drawable.tecsup_campus),"Campus Tecsup",Modifier.matchParentSize(),contentScale=ContentScale.Crop)
    Box(Modifier.matchParentSize().background(Brush.horizontalGradient(listOf(AzulNoche.copy(alpha=.98f),AzulNoche.copy(alpha=.64f)))))
    Column(Modifier.padding(22.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
     Text("CONECTADOS EN TECSUP",color=Cielo,style=MaterialTheme.typography.labelSmall)
     Text("¿Perdiste algo\nen el campus?",color=Color.White,style=MaterialTheme.typography.headlineMedium)
     Text("Encontrarlo puede ser más fácil\nde lo que crees",color=Color(0xFFD5E5FF),style=MaterialTheme.typography.bodyMedium)
     Button(onClick={abrir("buscar")},colors=ButtonDefaults.buttonColors(containerColor=Color.White,contentColor=AzulNoche),contentPadding=PaddingValues(horizontal=16.dp,vertical=10.dp)){Icon(Simbolos.Buscar,null,Modifier.size(17.dp));Spacer(Modifier.width(8.dp));Text("Explorar objetos")}
    }
   }}
   item {Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
    AccionInicio("Perdí algo","Vamos a buscarlo",false,Modifier.weight(1f)){abrir("publicar?tipo=PERDIDO")}
    AccionInicio("Encontré algo","Ayuda a devolverlo",true,Modifier.weight(1f)){abrir("publicar?tipo=ENCONTRADO")}
   }}
   if(categorias.isNotEmpty())item {
    Text("Explora por categoría",style=MaterialTheme.typography.titleLarge);Spacer(Modifier.height(10.dp))
    LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)) {
     item{FilterChip(selected=filtro.categoriaId==null,onClick={viewModel.elegirCategoria(null)},label={Text("Todo")})}
     items(categorias,key={it.id}){c->FilterChip(selected=filtro.categoriaId==c.id,onClick={viewModel.elegirCategoria(if(filtro.categoriaId==c.id)null else c.id)},label={Text(c.nombre?:"Categoría")},leadingIcon={Icon(Simbolos.Caja,null,Modifier.size(16.dp))})}
    }
   }
   errorCategorias?.let {item{AvisoEstado(it)}}
   if(comunidad.coincidencias.isNotEmpty())item {
    Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){Icon(Simbolos.Chispa,null,tint=MaterialTheme.colorScheme.tertiary);Text("Posibles coincidencias",style=MaterialTheme.typography.titleLarge)}
    Spacer(Modifier.height(12.dp))
    comunidad.coincidencias.forEach {par->Surface(onClick={abrir("pareja/${par.candidata.coincidenciaId?:"${par.origen}-${par.candidata.objetoId}"}")},shape=RoundedCornerShape(18.dp),color=MaterialTheme.colorScheme.tertiaryContainer,modifier=Modifier.fillMaxWidth().padding(bottom=8.dp)) {
     Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(par.candidata.nombre?:"Objeto encontrado",style=MaterialTheme.typography.titleMedium);Text("${par.candidata.porcentaje?.let {"%.0f".format(it)}?:"—"}% de similitud",style=MaterialTheme.typography.bodySmall)};Icon(Simbolos.Flecha,null)}
    }}}
   comunidad.aviso?.let {item{AvisoEstado(it)}}
   item{Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){Text("Publicaciones recientes",style=MaterialTheme.typography.titleLarge,modifier=Modifier.weight(1f));IconButton(onClick=viewModel::cargarRecientes){Icon(Simbolos.Ajustes,"Actualizar publicaciones")}}}
   state.aviso?.let {item{AvisoEstado(it);TextButton(onClick=viewModel::cargarRecientes){Text("Actualizar")}}}
   when {
    state.cargando->item{Esqueleto()}
    state.error!=null->item{AvisoEstado(state.error!!,true);BotonPrincipal("Reintentar",viewModel::cargarRecientes)}
    state.objetos.isEmpty()->item{Vacio("Cada objeto tiene una historia","Aún no hay publicaciones en esta selección. Puedes explorar otra categoría.")}
    else->items(state.objetos,key={it.id}){objeto->ObjetoCard(objeto,{onObjetoClick(objeto.id)})}
   }
  }
 }
}
@Composable private fun AccionInicio(titulo:String,subtitulo:String,encontrado:Boolean,modifier:Modifier,accion:()->Unit) {
 val fondo=if(encontrado)MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.primaryContainer
 val tinta=if(encontrado)MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onPrimaryContainer
 Surface(onClick=accion,modifier=modifier,shape=RoundedCornerShape(21.dp),color=fondo) {
  Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {
   Icon(if(encontrado)Simbolos.Caja else Simbolos.Buscar,null,Modifier.size(25.dp),tint=tinta)
   Text(titulo,style=MaterialTheme.typography.titleMedium,color=tinta)
   Text(subtitulo,style=MaterialTheme.typography.bodySmall,color=tinta)
  }
 }
}
