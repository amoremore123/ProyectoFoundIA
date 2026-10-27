package com.proyectointegrador.movil.ui.sprint
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import com.proyectointegrador.movil.model.*
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.proyectointegrador.movil.di.fabrica
import com.proyectointegrador.movil.ui.viewmodel.*
import com.proyectointegrador.movil.ui.components.*

@Composable fun ResumenObjeto(objeto:Objeto) {
 Panel {TipoChip(objeto.tipoSeguro);Text(objeto.nombre,style=MaterialTheme.typography.titleLarge);Text(objeto.descripcion?:"Sin descripción");FilaDato(Simbolos.Caja,objeto.categoria?.nombre?:"Sin categoría");FilaDato(Simbolos.Lugar,objeto.ubicacion?:"Sin ubicación");FilaDato(Simbolos.Fecha,objeto.fechaObjeto?:"Sin fecha");Text(objeto.estado?:"Sin estado",style=MaterialTheme.typography.labelSmall)}
}
@Composable fun PropiosScreen(volver:()->Unit,detalle:(Long)->Unit,sugerencias:(Long)->Unit,vm:PropiosViewModel=viewModel(factory=fabrica())) {
 val estado by vm.estado.collectAsStateWithLifecycle()
 Pagina("Mis publicaciones",volver){Consulta(estado,vm::recargar){objetos,recargar->
  TextButton(onClick=recargar){Text("Actualizar publicaciones")}
  if(objetos.isEmpty())Vacio("Tu historia empieza aquí","Publica lo que perdiste o encontraste para conectar con tu comunidad.",Simbolos.Caja)
  objetos.forEach {o->Panel{TipoChip(o.tipoSeguro);FotoObjeto(o,Modifier.fillMaxWidth().height(160.dp).clip(RoundedCornerShape(16.dp)));Text(o.nombre,style=MaterialTheme.typography.titleMedium);Text(o.estado?:"Sin estado",style=MaterialTheme.typography.labelSmall)
   if(o.estado in listOf("ACTIVO","RECUPERADO"))OutlinedButton(onClick={detalle(o.id)},modifier=Modifier.fillMaxWidth()){Text("Ver publicación")}
   if(o.tipo=="PERDIDO"&&o.estado=="ACTIVO")BotonPrincipal("Revisar coincidencias",{sugerencias(o.id)},icono=Simbolos.Chispa)
  }}
 }}
}
@Composable fun SugerenciasScreen(objetoId:Long,volver:()->Unit,abrir:(String)->Unit,vm:SugerenciasViewModel=viewModel(key="sugerencias-$objetoId",factory=fabrica(objetoId=objetoId))) {
 val estado by vm.estado.collectAsStateWithLifecycle()
 Pagina("Posibles coincidencias",volver){Consulta(estado,vm::recargar){datos,recargar->
  Text(datos.objeto.nombre,style=MaterialTheme.typography.titleLarge)
  if(!vm.puedeGestionar)AvisoEstado("Solo el titular del objeto perdido puede gestionar sus coincidencias.")
  else {
   if(datos.candidatas.isEmpty())Vacio("Seguimos buscando contigo","Aún no hay coincidencias disponibles. Puedes volver a consultar más tarde.",Simbolos.Chispa)
   datos.candidatas.forEach {c->Panel{Row(verticalAlignment=Alignment.CenterVertically){Icon(Simbolos.Chispa,null,tint=MaterialTheme.colorScheme.tertiary);Spacer(Modifier.width(10.dp));Text("${c.porcentaje?.let {"%.0f".format(it)}?:"—"}% de similitud",style=MaterialTheme.typography.titleMedium)};Text(c.nombre?:"Objeto encontrado");c.ubicacion?.let {FilaDato(Simbolos.Lugar,it)};BotonPrincipal("Comparar ambos objetos",{abrir(c.coincidenciaId?:"$objetoId-${c.objetoId}")})}}
   OutlinedButton(onClick=recargar){Text("Actualizar coincidencias")}
  }
 }}
}
@Composable fun ParejaScreen(id:String,volver:()->Unit,propios:()->Unit,vm:ParejaViewModel=viewModel(key="pareja-$id",factory=fabrica(parejaId=id))) {
 val estado by vm.estado.collectAsStateWithLifecycle();val accion by vm.accion.collectAsStateWithLifecycle();val resultado by vm.resultado.collectAsStateWithLifecycle();val confirmar by vm.confirmacion.collectAsStateWithLifecycle()
 Pagina("Conecta las pistas",volver){
  if(resultado!=null){IlustracionEncuentro(2,Modifier.fillMaxWidth().heightIn(max=210.dp));AvisoEstado(resultado!!);BotonPrincipal("Volver a mis publicaciones",propios)}
  else Consulta(estado,vm::recargar){datos,_->val pareja=datos.pareja
   Surface(shape=RoundedCornerShape(24.dp),color=MaterialTheme.colorScheme.tertiaryContainer,modifier=Modifier.fillMaxWidth()) {Column(Modifier.padding(22.dp),verticalArrangement=Arrangement.spacedBy(10.dp)) {
    Row(verticalAlignment=Alignment.CenterVertically){Icon(Simbolos.Chispa,null);Spacer(Modifier.width(10.dp));Text("${"%.0f".format(pareja.porcentaje)}%",style=MaterialTheme.typography.displaySmall);Spacer(Modifier.width(12.dp));Text("DE SIMILITUD\n${pareja.nivel}",style=MaterialTheme.typography.labelSmall)}
    LinearProgressIndicator(progress={pareja.porcentaje.toFloat().div(100).coerceIn(0f,1f)},modifier=Modifier.fillMaxWidth(),color=MaterialTheme.colorScheme.tertiary)
    Text("Una pista, no una prueba de propiedad. Compara los detalles antes de actuar.",style=MaterialTheme.typography.bodySmall)
   }}
   Row(horizontalArrangement=Arrangement.spacedBy(10.dp)){listOf(pareja.perdido,pareja.encontrado).forEach {o->Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(8.dp)){FotoObjeto(o,Modifier.fillMaxWidth().height(150.dp).clip(RoundedCornerShape(18.dp)));TipoChip(o.tipoSeguro);Text(o.nombre,style=MaterialTheme.typography.titleSmall)}}}
   Panel("Características comparadas"){pareja.criterios.forEach {(criterio,puntos)->Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween){Text(criterio.replaceFirstChar {it.uppercase()},Modifier.weight(1f));Text("$puntos puntos",style=MaterialTheme.typography.labelMedium,color=MaterialTheme.colorScheme.tertiary)}}}
   ResumenObjeto(pareja.perdido);ResumenObjeto(pareja.encontrado)
   EstadoAccion(accion)
   if(vm.puedeGestionar){BotonPrincipal("Confirmar recuperación",{vm.pedir("recuperar")},!accion.ocupada,icono=Simbolos.Check);OutlinedButton(onClick={vm.pedir("contactar")},enabled=!accion.ocupada,modifier=Modifier.fillMaxWidth()){Text("Solicitar contacto")};TextButton(onClick={vm.pedir("descartar")},enabled=!accion.ocupada,modifier=Modifier.fillMaxWidth()){Text("Descartar coincidencia")}}
   else AvisoEstado("La gestión corresponde al titular del objeto perdido activo.")
  }
 }
 confirmar?.let {tipo->Confirmacion(when(tipo){"recuperar"->"¿Recuperaste este objeto?";"contactar"->"¿Solicitar contacto?";else->"¿Descartar esta coincidencia?"},when(tipo){"recuperar"->"Se registrará la recuperación de tu objeto perdido. El hallazgo de la otra persona se conserva.";"contactar"->"Se registrará una solicitud de contacto para esta pareja en el servidor.";else->"La pareja dejará de aparecer en tus sugerencias. Las demás coincidencias se conservan."},accion,vm::cancelar,vm::confirmar)}
}
