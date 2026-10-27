package com.proyectointegrador.movil.ui.screens
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import com.proyectointegrador.movil.di.fabrica
import com.proyectointegrador.movil.ui.components.*
import com.proyectointegrador.movil.ui.viewmodel.PublicarViewModel
import com.proyectointegrador.movil.ui.sprint.EstadoAccion
import java.text.SimpleDateFormat
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable fun PublicarScreen(onPublicado:(Long)->Unit={},tipoInicial:String="PERDIDO",viewModel:PublicarViewModel=viewModel(factory=fabrica())) {
 val estado by viewModel.estado.collectAsStateWithLifecycle();val f by viewModel.form.collectAsStateWithLifecycle();val accion by viewModel.accion.collectAsStateWithLifecycle();val evento by viewModel.evento.collectAsStateWithLifecycle()
 LaunchedEffect(Unit){if(f.nombre.isBlank()&&tipoInicial in listOf("PERDIDO","ENCONTRADO"))viewModel.editar("tipo",tipoInicial)}
 val selector=rememberLauncherForActivityResult(ActivityResultContracts.GetContent()){uri->uri?.let {viewModel.foto(it.toString())}}
 var calendario by remember {mutableStateOf(false)}
 val fechaPicker=rememberDatePickerState(selectableDates=object:SelectableDates{override fun isSelectableDate(utcTimeMillis:Long)=utcTimeMillis<=System.currentTimeMillis()})
 val titulos=listOf("Tipo de publicación","Información del objeto","Fotografía","Ubicación","Fecha","Revisa y publica")
 if(evento!=null) {
  com.proyectointegrador.movil.ui.sprint.Pagina("¡Ya está publicado!"){
   IlustracionEncuentro(2,Modifier.fillMaxWidth().heightIn(max=230.dp));Text("Un paso más cerca de reencontrarse.",style=MaterialTheme.typography.headlineMedium)
   AvisoEstado("Tu publicación se guardó en el servidor y está disponible para la comunidad.")
   BotonPrincipal("Ver publicación",{val id=evento?.toLongOrNull();viewModel.consumirEvento();if(id!=null)onPublicado(id)})
  };return
 }
 com.proyectointegrador.movil.ui.sprint.Pagina("Comparte y conecta") {
  Text("Ayuda a que cada objeto vuelva a su lugar.",color=MaterialTheme.colorScheme.onSurfaceVariant)
  Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){repeat(6){indice->Surface(color=if(indice<=f.paso)MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant,shape=RoundedCornerShape(5.dp),modifier=Modifier.weight(1f).height(5.dp)) {}}}
  Text("PASO ${f.paso+1} DE 6",style=MaterialTheme.typography.labelSmall,color=MaterialTheme.colorScheme.primary)
  Panel(titulos[f.paso]) {
   when(f.paso) {
    0->{Text("¿Qué te trae por aquí?",style=MaterialTheme.typography.headlineSmall)
     listOf("PERDIDO" to "Perdí un objeto","ENCONTRADO" to "Encontré un objeto").forEach {(clave,texto)->OutlinedCard(onClick={viewModel.editar("tipo",clave)},modifier=Modifier.fillMaxWidth(),colors=CardDefaults.outlinedCardColors(containerColor=if(f.tipo==clave)MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface)) {
      Row(Modifier.padding(18.dp),verticalAlignment=Alignment.CenterVertically){Icon(if(clave=="PERDIDO")Simbolos.Buscar else Simbolos.Caja,null);Spacer(Modifier.width(14.dp));Text(texto,Modifier.weight(1f));RadioButton(f.tipo==clave,onClick=null)}
     }}}
    1->{CampoPublicacion("Nombre del objeto",f.nombre,{viewModel.editar("nombre",it)},f.errores["nombre"])
     CampoPublicacion("Descríbelo: color, marca, detalles…",f.descripcion,{viewModel.editar("descripcion",it)},f.errores["descripcion"],3)
     Text("Categoría",style=MaterialTheme.typography.titleSmall);estado.datos?.aviso?.let {AvisoEstado(it)}
     Column {estado.datos?.datos.orEmpty().filter {it.estado}.forEach {c->FilterChip(f.categoriaId==c.id,{viewModel.categoria(c.id)},{Text(c.nombre?:"Categoría")})}}
     f.errores["categoria"]?.let {AvisoEstado(it,true)}
     if(estado.cargando)LinearProgressIndicator(Modifier.fillMaxWidth())
     estado.error?.let {AvisoEstado(it,true);TextButton(onClick=viewModel::recargar){Text("Reintentar categorías")}}
    }
    2->{Text("Una foto ayuda a reconocerlo.",style=MaterialTheme.typography.titleMedium);Text("JPEG o PNG · máximo 5 MiB. Opcional.",style=MaterialTheme.typography.bodySmall)
     if(f.foto!=null)AsyncImage(model=f.foto,contentDescription="Vista previa de la fotografía elegida",modifier=Modifier.fillMaxWidth().height(210.dp).clip(RoundedCornerShape(18.dp)),contentScale=ContentScale.Crop)
     else Vacio("Dale una imagen a tu publicación","Elige una fotografía real de tu objeto.",Simbolos.Foto)
     OutlinedButton(onClick={selector.launch("image/*")},enabled=!accion.ocupada&&f.publicadoId==null,modifier=Modifier.fillMaxWidth()){Icon(Simbolos.Foto,null);Spacer(Modifier.width(8.dp));Text("Elegir fotografía")}
     if(f.foto!=null)TextButton(onClick={viewModel.foto(null)},enabled=f.publicadoId==null){Text("Quitar fotografía")}
    }
    3->{Text("¿Dónde ocurrió?",style=MaterialTheme.typography.headlineSmall);Text("Indica el pabellón, aula o punto de referencia del campus.",color=MaterialTheme.colorScheme.onSurfaceVariant)
     CampoPublicacion("Ubicación",f.ubicacion,{viewModel.editar("ubicacion",it)},f.errores["ubicacion"])}
    4->{Text("¿Cuándo ocurrió?",style=MaterialTheme.typography.headlineSmall);OutlinedButton(onClick={calendario=true},modifier=Modifier.fillMaxWidth()){Icon(Simbolos.Fecha,null);Spacer(Modifier.width(10.dp));Text(f.fecha.ifBlank {"Elegir fecha"})};f.errores["fecha"]?.let {AvisoEstado(it,true)}}
    5->{TipoChip(f.tipo);Text(f.nombre.ifBlank {"Falta el nombre"},style=MaterialTheme.typography.titleLarge);Text(f.descripcion.ifBlank {"Completa la descripción del objeto."})
     FilaDato(Simbolos.Lugar,f.ubicacion.ifBlank {"Falta la ubicación"});FilaDato(Simbolos.Fecha,f.fecha.ifBlank {"Falta la fecha"})
     Text("Categoría: ${estado.datos?.datos?.find {it.id==f.categoriaId}?.nombre?:"Sin elegir"}")
     f.foto?.let {AsyncImage(it,"Foto para publicar",Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(16.dp)),contentScale=ContentScale.Crop)}
     if(f.errores.isNotEmpty())AvisoEstado(f.errores.values.joinToString("\n"),true)
     Text("La publicación se enviará a la comunidad Tecsup.",style=MaterialTheme.typography.bodySmall)
    }
   }
  }
  EstadoAccion(accion)
  if(f.paso<5)BotonPrincipal("Siguiente",viewModel::siguiente,!accion.ocupada)
  else BotonPrincipal(if(f.publicadoId==null)"Publicar objeto" else "Reintentar fotografía",viewModel::publicar,!accion.ocupada&&!estado.cargando&&estado.error==null)
  if(f.publicadoId!=null)TextButton(onClick=viewModel::terminarSinFoto,enabled=!accion.ocupada){Text("Ver publicación sin la fotografía pendiente")}
  else if(f.paso>0)TextButton(onClick={viewModel.paso(f.paso-1)},enabled=!accion.ocupada){Text("Paso anterior")}
 }
 if(calendario)DatePickerDialog(onDismissRequest={calendario=false},confirmButton={TextButton(onClick={fechaPicker.selectedDateMillis?.let {fecha->val formato=SimpleDateFormat("yyyy-MM-dd",Locale.ROOT).apply {timeZone=TimeZone.getTimeZone("UTC")};viewModel.editar("fecha",formato.format(Date(fecha)))};calendario=false}){Text("Confirmar fecha")}},dismissButton={TextButton(onClick={calendario=false}){Text("Cancelar")}}){DatePicker(fechaPicker,title={Text("Fecha del objeto",Modifier.padding(20.dp))})}
}
@Composable private fun CampoPublicacion(titulo:String,valor:String,cambiar:(String)->Unit,error:String?,lineas:Int=1) {
 OutlinedTextField(value=valor,onValueChange=cambiar,label={Text(titulo)},modifier=Modifier.fillMaxWidth(),isError=error!=null,minLines=lineas,singleLine=lineas==1,shape=MaterialTheme.shapes.medium,supportingText=error?.let {{Text(it)}})
}
