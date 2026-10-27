package com.proyectointegrador.movil.ui.screens

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.proyectointegrador.movil.ui.components.*
import com.proyectointegrador.movil.ui.viewmodel.ExperienciaViewModel
import com.proyectointegrador.movil.ui.sprint.EstadoAccion

@Composable fun BienvenidaScreen(vm:ExperienciaViewModel,abrir:(String)->Unit) {
 var paso by rememberSaveable {mutableIntStateOf(-1)}
 val accion by vm.accion.collectAsStateWithLifecycle()
 Column(Modifier.fillMaxSize().background(DegradadoMarca).statusBarsPadding().navigationBarsPadding().verticalScroll(rememberScrollState()).padding(horizontal=26.dp,vertical=20.dp),verticalArrangement=Arrangement.spacedBy(16.dp)) {
  Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.SpaceBetween) {
   LogoFoundia(claro=true)
   Text("COMUNIDAD\nTECSUP",color=Color(0xFFB4D7FF),style=MaterialTheme.typography.labelSmall)
  }
  if(paso>=0) Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.End){TextButton(onClick={vm.completar()},enabled=!accion.ocupada){Text("Omitir",color=Color.White)}}
  AnimatedContent(targetState=paso,label="Introducción") { actual ->
   Column(verticalArrangement=Arrangement.spacedBy(18.dp)) {
    IlustracionEncuentro(actual.coerceAtLeast(0),Modifier.fillMaxWidth().heightIn(max=300.dp))
    Text(if(actual<0)"ENCUENTRA LO QUE TE CONECTA" else listOf("01 / ENCUENTRA","02 / PUBLICA","03 / CONECTA")[actual],color=Cielo,style=MaterialTheme.typography.labelMedium)
    Text(if(actual<0)"Lo que perdiste puede estar más cerca de lo que imaginas." else listOf("¿Perdiste algo? Estamos aquí para ayudarte.","Publica lo que encontraste o perdiste en pocos pasos.","Descubre coincidencias y recupera lo que es tuyo.")[actual],color=Color.White,style=MaterialTheme.typography.headlineLarge)
    Text(if(actual<0)"Encuentra, publica y recupera objetos dentro de la comunidad Tecsup." else listOf("Explora las publicaciones de tu comunidad y vuelve a encontrar eso que importa.","Una foto, los detalles y el lugar: ayuda a que cada objeto encuentre a su dueño.","Compara las sugerencias reales y confirma la recuperación de tu objeto.")[actual],color=Color(0xFFCCDCF7),style=MaterialTheme.typography.bodyLarge)
   }
  }
  Spacer(Modifier.height(4.dp))
  if(paso>=0) Row(horizontalArrangement=Arrangement.spacedBy(7.dp)){repeat(3){Surface(color=if(it==paso)Cielo else Color(0xFF5670A4),shape=CircleShape,modifier=Modifier.width(if(it==paso)26.dp else 8.dp).height(8.dp)) {}}}
  EstadoAccion(accion)
  Button(onClick={if(paso<2)paso++ else vm.completar()},enabled=!accion.ocupada,modifier=Modifier.fillMaxWidth().heightIn(min=56.dp),colors=ButtonDefaults.buttonColors(containerColor=Color.White,contentColor=AzulNoche)) {
   Text(if(paso<0||paso==2)"Comenzar" else "Siguiente",Modifier.weight(1f));Icon(Simbolos.Flecha,null)
  }
  TextButton(onClick={vm.completar("perfil")},enabled=!accion.ocupada,modifier=Modifier.fillMaxWidth()){Text("Ya tengo una cuenta",color=Color.White)}
 }
}
