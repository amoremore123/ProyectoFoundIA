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

@Composable fun InicioScreen(onObjetoClick:(Long)->Unit,abrir:(String)->Unit={},comunidad:ComunidadEstado=ComunidadEstado(),autenticado:Boolean=false,viewModel:ObjetosViewModel=viewModel(factory=fabrica())) {
 val state by viewModel.state.collectAsStateWithLifecycle();val categorias by viewModel.categorias.collectAsStateWithLifecycle();val filtro by viewModel.busqueda.collectAsStateWithLifecycle();val errorCategorias by viewModel.errorCategorias.collectAsStateWithLifecycle()
 LaunchedEffect(Unit){viewModel.cargarRecientes();viewModel.cargarCategorias()}
 Surface(color=MarinoFindi,modifier=Modifier.fillMaxSize()) {
  LazyColumn(Modifier.statusBarsPadding().background(MaterialTheme.colorScheme.background),contentPadding=PaddingValues(start=16.dp,end=16.dp,top=8.dp,bottom=24.dp),verticalArrangement=Arrangement.spacedBy(20.dp)) {
   item {
    Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(28.dp)).background(DegradadoMarca).padding(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)) {
     Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically) {
      LogoFoundia(claro=true,modifier=Modifier.weight(1f).wrapContentWidth(Alignment.Start))
      IconButton(onClick={abrir("avisos")}) {
       BadgedBox(badge={if(comunidad.sinLeer>0)Badge(containerColor=Color(0xFFFF8500)){Text(comunidad.sinLeer.toString())}}) {
        Icon(Simbolos.Avisos,"Notificaciones",tint=Color.White)
       }
      }
      Surface(onClick={abrir("perfil")},shape=CircleShape,color=AzulFindi,modifier=Modifier.size(38.dp)) {
       Box(contentAlignment=Alignment.Center) { Text(comunidad.usuario?.nombre?.take(1)?.uppercase()?:"Tú",color=Color.White,style=MaterialTheme.typography.labelLarge) }
      }
     }
     Column {
      Text(comunidad.usuario?.let {"¡Hola, ${it.nombre.split(" ").first()}!"}?:"¡Hola, comunidad!",color=Color.White,style=MaterialTheme.typography.titleLarge)
      Text("¿En qué podemos ayudarte hoy?",color=Color(0xFFD6E6FF),style=MaterialTheme.typography.bodySmall)
     }
     EscenaFindi(Modifier.fillMaxWidth().height(220.dp),abrir={abrir("asistente")})

    }
   }
   item { Column(verticalArrangement=Arrangement.spacedBy(12.dp)) {
    Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
     TarjetaFindi("Reportar\nobjeto",Simbolos.Caja,AmarilloFindi,Modifier.weight(1f)){abrir("publicar?tipo=PERDIDO")}
     TarjetaFindi("Buscar\nobjeto",Simbolos.Buscar,AzulFindi,Modifier.weight(1f)){abrir("buscar")}
    }
    Row(horizontalArrangement=Arrangement.spacedBy(12.dp)) {
     TarjetaFindi("Mis\nreportes",Simbolos.Ajustes,AzulFindi,Modifier.weight(1f)){abrir(if(autenticado)"propios" else "perfil")}
     TarjetaFindi("Notificaciones",Simbolos.Avisos,Color(0xFFFF8500),Modifier.weight(1f)){abrir("avisos")}
    }
    Surface(onClick={abrir("asistente")},shape=RoundedCornerShape(20.dp),color=MaterialTheme.colorScheme.primaryContainer) {
     Row(Modifier.fillMaxWidth().padding(16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
      Icon(Simbolos.Escudo,null,tint=AzulFindi)
      Text("Recupera tus objetos de forma segura",Modifier.weight(1f),style=MaterialTheme.typography.titleSmall)
      Icon(Simbolos.Flecha,null,Modifier.size(18.dp))
     }
    }
    TextButton(onClick={abrir("publicar?tipo=ENCONTRADO")}) { Text("¿Encontraste algo? Ayuda a devolverlo") }
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
