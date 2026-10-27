package com.proyectointegrador.movil.ui.screens
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.proyectointegrador.movil.di.fabrica
import com.proyectointegrador.movil.ui.components.*
import com.proyectointegrador.movil.ui.viewmodel.ObjetosViewModel

@Composable fun BuscarScreen(onObjetoClick:(Long)->Unit,viewModel:ObjetosViewModel=viewModel(factory=fabrica())) {
 val state by viewModel.state.collectAsStateWithLifecycle();val f by viewModel.busqueda.collectAsStateWithLifecycle();val categorias by viewModel.categorias.collectAsStateWithLifecycle()
 LaunchedEffect(Unit){viewModel.buscarActual();viewModel.cargarCategorias()}
 Surface(color=MaterialTheme.colorScheme.background,modifier=Modifier.fillMaxSize()) {
  LazyColumn(Modifier.statusBarsPadding().imePadding(),contentPadding=PaddingValues(20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
   item{Cabecera("Encuentra eso\nque importa","Busca entre las publicaciones de tu comunidad.")}
   item{OutlinedTextField(value=f.texto,onValueChange=viewModel::editarBusqueda,placeholder={Text("¿Qué estás buscando?")},leadingIcon={Icon(Simbolos.Buscar,null)},singleLine=true,shape=MaterialTheme.shapes.large,modifier=Modifier.fillMaxWidth())}
   item{Row(horizontalArrangement=Arrangement.spacedBy(8.dp),modifier=Modifier.horizontalScroll(rememberScrollState())){listOf(null to "Todos","PERDIDO" to "Perdidos","ENCONTRADO" to "Encontrados").forEach {(v,t)->FilterChip(selected=f.tipo==v,onClick={viewModel.elegirTipo(v)},label={Text(t)})}}}
   if(categorias.isNotEmpty())item{LazyRow(horizontalArrangement=Arrangement.spacedBy(8.dp)){item{FilterChip(f.categoriaId==null,{viewModel.elegirCategoria(null)},{Text("Todas las categorías")})};items(categorias,key={it.id}){c->FilterChip(f.categoriaId==c.id,{viewModel.elegirCategoria(c.id)},{Text(c.nombre?:"Categoría")})}}}
   if(f.tipo!=null||f.categoriaId!=null)item{TextButton(onClick={viewModel.elegirTipo(null);viewModel.elegirCategoria(null)}){Text("Limpiar filtros")}}
   state.aviso?.let {item{AvisoEstado(it);TextButton(onClick=viewModel::buscarActual){Text("Actualizar resultados")}}}
   when {
    state.cargando->item{Esqueleto()}
    state.error!=null->item{AvisoEstado(state.error!!,true);BotonPrincipal("Reintentar",viewModel::buscarActual)}
    state.objetos.isEmpty()->item{Vacio("Todavía no lo encontramos","Prueba otra palabra o amplía los filtros. Tu próxima búsqueda puede estar más cerca.")}
    else->{item{Text("${state.objetos.size} publicaciones",style=MaterialTheme.typography.titleMedium)};items(state.objetos,key={it.id}){o->ObjetoCard(o,{onObjetoClick(o.id)},compacto=true)}}
   }
  }
 }
}
