package com.proyectointegrador.movil.ui.components

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.*
import androidx.compose.ui.graphics.vector.*
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.proyectointegrador.movil.R

val AzulNoche=Color(0xFF0C1E4B)
val AzulElectrico=Color(0xFF2563EB)
val Cielo=Color(0xFF5AD5F4)
val Violeta=Color(0xFFA18BF8)
val DegradadoMarca=Brush.linearGradient(listOf(AzulNoche,Color(0xFF193C8A),Color(0xFF2862D6)))

/** Trazos vectoriales propios, consistentes a 24 unidades. */
object Simbolos {
 private fun crear(nombre:String,vararg trazos:String)=ImageVector.Builder(nombre,24.dp,24.dp,24f,24f).apply {
  trazos.forEach { addPath(PathParser().parsePathString(it).toNodes(),stroke=SolidColor(Color.Black),strokeLineWidth=1.7f,strokeLineCap=StrokeCap.Round,strokeLineJoin=StrokeJoin.Round) }
 }.build()
 val Inicio=crear("Inicio","M3 10 L12 3 L21 10 V21 H15 V14 H9 V21 H3 Z")
 val Buscar=crear("Buscar","M16 16 L21 21 M18 10 A8 8 0 1 1 2 10 A8 8 0 1 1 18 10")
 val Publicar=crear("Publicar","M12 5 V19 M5 12 H19")
 val Avisos=crear("Avisos","M18 8 A6 6 0 0 0 6 8 V14 L3 17 H21 L18 14 Z M10 21 H14")
 val Perfil=crear("Perfil","M16 7 A4 4 0 1 1 8 7 A4 4 0 1 1 16 7 M4 21 V19 A8 6 0 0 1 20 19 V21")
 val Flecha=crear("Flecha","M5 12 H19 M14 7 L19 12 L14 17")
 val Volver=crear("Volver","M19 12 H5 M10 7 L5 12 L10 17")
 val Lugar=crear("Lugar","M20 10 C20 16 12 22 12 22 C12 22 4 16 4 10 A8 8 0 1 1 20 10 M15 10 A3 3 0 1 1 9 10 A3 3 0 1 1 15 10")
 val Fecha=crear("Fecha","M4 5 H20 V21 H4 Z M8 2 V8 M16 2 V8 M4 11 H20 M8 15 H10 M14 15 H16")
 val Correo=crear("Correo","M3 5 H21 V19 H3 Z M3 6 L12 13 L21 6")
 val Candado=crear("Candado","M5 10 H19 V21 H5 Z M8 10 V6 A4 4 0 0 1 16 6 V10 M12 14 V17")
 val Ojo=crear("Ojo","M2 12 Q12 -2 22 12 Q12 26 2 12 M15 12 A3 3 0 1 1 9 12 A3 3 0 1 1 15 12")
 val Foto=crear("Foto","M3 3 H21 V21 H3 Z M3 17 L9 11 L14 16 L18 12 L21 15 M9 7 A1 1 0 1 1 7 7 A1 1 0 1 1 9 7")
 val Caja=crear("Caja","M3 7 L12 2 L21 7 V18 L12 23 L3 18 Z M3 7 L12 12 L21 7 M12 12 V23 M8 4 L17 9")
 val Chispa=crear("Chispa","M12 2 L15 9 L22 12 L15 15 L12 22 L9 15 L2 12 L9 9 Z")
 val Ajustes=crear("Ajustes","M3 6 H21 M3 12 H21 M3 18 H21 M8 3 V9 M16 9 V15 M8 15 V21")
 val Salir=crear("Salir","M10 3 H3 V21 H10 M8 12 H22 M17 7 L22 12 L17 17")
 val Escudo=crear("Escudo","M12 2 L21 6 V12 Q21 19 12 22 Q3 19 3 12 V6 Z M8 12 L11 15 L16 9")
 val Luna=crear("Luna","M21 14 A9 9 0 1 1 10 3 A8 8 0 0 0 21 14")
 val Check=crear("Confirmado","M5 12 L10 17 L20 6")
 val Compartir=crear("Compartir","M7 12 L18 5 M7 12 L18 19 M7 12 A2 2 0 1 1 3 12 A2 2 0 1 1 7 12 M21 4 A2 2 0 1 1 17 4 A2 2 0 1 1 21 4 M21 20 A2 2 0 1 1 17 20 A2 2 0 1 1 21 20")
}
@Composable fun LogoFoundia(claro:Boolean=false,modifier:Modifier=Modifier) {
 val oscuro=MaterialTheme.colorScheme.background.luminance()<.25f
 Image(painterResource(if(claro||oscuro)R.drawable.foundia_logo_claro else R.drawable.foundia_logo),"FoundIA",modifier.width(125.dp).height(38.dp),contentScale=ContentScale.Fit)
}
@Composable fun Cabecera(titulo:String,subtitulo:String?=null,volver:(()->Unit)?=null,accion:(@Composable ()->Unit)?=null) {
 Column(Modifier.fillMaxWidth().padding(top=12.dp,bottom=8.dp)) {
  Row(verticalAlignment=Alignment.CenterVertically) {
   if(volver!=null) IconButton(onClick=volver){Icon(Simbolos.Volver,"Volver")}
   Column(Modifier.weight(1f)){Text(titulo,style=MaterialTheme.typography.headlineMedium);subtitulo?.let {Text(it,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant)}}
   accion?.invoke()
  }
 }
}
@Composable fun BotonPrincipal(texto:String,onClick:()->Unit,enabled:Boolean=true,modifier:Modifier=Modifier,icono:ImageVector=Simbolos.Flecha) {
 Button(onClick=onClick,enabled=enabled,modifier=modifier.fillMaxWidth().heightIn(min=54.dp),shape=RoundedCornerShape(17.dp),contentPadding=PaddingValues(horizontal=20.dp,vertical=14.dp)) {
  Text(texto,Modifier.weight(1f),style=MaterialTheme.typography.labelLarge);Spacer(Modifier.width(12.dp));Icon(icono,null,Modifier.size(20.dp))
 }
}
@Composable fun Panel(titulo:String?=null,contenido:@Composable ColumnScope.()->Unit) {
 Surface(shape=RoundedCornerShape(22.dp),color=MaterialTheme.colorScheme.surface,border=BorderStroke(1.dp,MaterialTheme.colorScheme.outlineVariant),modifier=Modifier.fillMaxWidth()) {
  Column(Modifier.padding(18.dp),verticalArrangement=Arrangement.spacedBy(12.dp)){titulo?.let {Text(it,style=MaterialTheme.typography.titleMedium)};contenido()}
 }
}
@Composable fun AvisoEstado(texto:String,error:Boolean=false) {
 Surface(color=if(error)MaterialTheme.colorScheme.errorContainer else MaterialTheme.colorScheme.secondaryContainer,shape=RoundedCornerShape(14.dp),modifier=Modifier.fillMaxWidth()) {
  Text(texto,Modifier.padding(14.dp),style=MaterialTheme.typography.bodySmall,color=if(error)MaterialTheme.colorScheme.onErrorContainer else MaterialTheme.colorScheme.onSecondaryContainer)
 }
}
@Composable fun Vacio(titulo:String,mensaje:String,icono:ImageVector=Simbolos.Buscar) {
 Column(Modifier.fillMaxWidth().padding(vertical=28.dp,horizontal=18.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(12.dp)) {
  Surface(color=MaterialTheme.colorScheme.primaryContainer,shape=RoundedCornerShape(22.dp)){Icon(icono,null,Modifier.padding(18.dp).size(32.dp),tint=MaterialTheme.colorScheme.primary)}
  Text(titulo,style=MaterialTheme.typography.titleMedium,textAlign=androidx.compose.ui.text.style.TextAlign.Center)
  Text(mensaje,style=MaterialTheme.typography.bodyMedium,color=MaterialTheme.colorScheme.onSurfaceVariant,textAlign=androidx.compose.ui.text.style.TextAlign.Center)
 }
}
@Composable fun Esqueleto() {
 Column(verticalArrangement=Arrangement.spacedBy(12.dp)){repeat(2){
  Surface(shape=RoundedCornerShape(22.dp),color=MaterialTheme.colorScheme.surfaceVariant,modifier=Modifier.fillMaxWidth().height(175.dp)){}
  Surface(shape=RoundedCornerShape(8.dp),color=MaterialTheme.colorScheme.surfaceVariant,modifier=Modifier.fillMaxWidth(.65f).height(18.dp)){}
 }}
}
@Composable fun FilaDato(icono:ImageVector,texto:String) {
 Row(horizontalArrangement=Arrangement.spacedBy(8.dp),verticalAlignment=Alignment.CenterVertically){Icon(icono,null,Modifier.size(17.dp),tint=MaterialTheme.colorScheme.primary);Text(texto,style=MaterialTheme.typography.bodySmall,color=MaterialTheme.colorScheme.onSurfaceVariant)}
}
/** Ilustración vectorial original: mochila, publicación y búsqueda; no representa fotografías reales. */
@Composable fun IlustracionEncuentro(paso:Int=0,modifier:Modifier=Modifier) {
 Canvas(modifier.aspectRatio(1.36f)) {
  val sx=size.width/340f;val sy=size.height/250f
  withTransform({scale(sx,sy,pivot=Offset.Zero)}) {
   drawCircle(Brush.radialGradient(listOf(Color(0x553F8DFF),Color.Transparent),center=Offset(170f,130f),radius=130f),130f,Offset(170f,130f))
   drawOval(Color(0x33204DB0),Offset(52f,223f),Size(248f,18f))
   drawCircle(Color(0x335AD5F4),107f,Offset(170f,123f),style=Stroke(1f))
   drawCircle(Color(0x226AA5FF),126f,Offset(170f,123f),style=Stroke(1f))
   rotate(-10f,Offset(130f,140f)) {
    drawRoundRect(Color(0xFF3456B2),Offset(65f,73f),Size(134f,155f),CornerRadius(32f))
    drawRoundRect(Brush.verticalGradient(listOf(Color(0xFF91B7FF),Color(0xFF497DEC)),50f,230f),Offset(58f,65f),Size(134f,155f),CornerRadius(30f))
    drawRoundRect(Color(0xFF244799),Offset(97f,47f),Size(55f,40f),CornerRadius(17f),style=Stroke(10f))
    drawRoundRect(Color(0xFF416FCE),Offset(75f,137f),Size(100f,60f),CornerRadius(19f))
    drawLine(Color(0xFFA8D8FF),Offset(84f,150f),Offset(165f,150f),3f,StrokeCap.Round)
    drawRoundRect(Color(0xFFFFDF91),Offset(112f,94f),Size(30f,14f),CornerRadius(4f))
    drawLine(Color(0xFFB6D6FF),Offset(67f,92f),Offset(67f,133f),3f,StrokeCap.Round)
   }
   rotate(8f,Offset(245f,95f)) {
    drawRoundRect(Color(0xFFF3F8FF),Offset(190f,33f),Size(99f,128f),CornerRadius(17f))
    drawRoundRect(Color(0xFFD7E9FF),Offset(201f,45f),Size(77f,63f),CornerRadius(10f))
    drawRoundRect(Color(0xFF8F78E8),Offset(219f,55f),Size(42f,42f),CornerRadius(12f))
    drawLine(Color(0xFF8FA9CF),Offset(204f,122f),Offset(263f,122f),5f,StrokeCap.Round)
    drawLine(Color(0xFFCFDCF0),Offset(204f,136f),Offset(246f,136f),5f,StrokeCap.Round)
   }
   if(paso==0){
    drawLine(Color(0xFFB2DEFF),Offset(248f,191f),Offset(283f,225f),16f,StrokeCap.Round)
    drawCircle(Color(0x773181D3),38f,Offset(225f,165f));drawCircle(Color(0xFFD1EEFF),38f,Offset(225f,165f),style=Stroke(9f))
    drawArc(Color.White,-140f,65f,false,Offset(197f,137f),Size(56f,56f),style=Stroke(3f,cap=StrokeCap.Round))
   } else {
    drawRoundRect(if(paso==1)Color(0xFF7D63DC) else Color(0xFF36B7C4),Offset(192f,168f),Size(112f,45f),CornerRadius(15f))
    if(paso==1){drawLine(Color.White,Offset(225f,190f),Offset(271f,190f),4f,StrokeCap.Round);drawLine(Color.White,Offset(248f,179f),Offset(248f,201f),4f,StrokeCap.Round)}
    else {drawLine(Color.White,Offset(224f,190f),Offset(236f,200f),5f,StrokeCap.Round);drawLine(Color.White,Offset(236f,200f),Offset(266f,180f),5f,StrokeCap.Round)}
   }
   drawCircle(Cielo,5f,Offset(42f,75f));drawCircle(Violeta,4f,Offset(301f,139f));drawCircle(Color(0xFFFFDF91),3f,Offset(172f,24f))
   drawLine(Cielo,Offset(38f,171f),Offset(50f,171f),2f);drawLine(Cielo,Offset(44f,165f),Offset(44f,177f),2f)
  }
 }
}
