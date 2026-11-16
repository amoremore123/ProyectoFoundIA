package com.proyectointegrador.movil.ui.screens

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.proyectointegrador.movil.ui.components.*
import com.proyectointegrador.movil.ui.viewmodel.ExperienciaViewModel

@Composable fun BienvenidaScreen(vm: ExperienciaViewModel, abrir: (String) -> Unit) {
    val accion by vm.accion.collectAsStateWithLifecycle()
    val pequena = LocalConfiguration.current.screenHeightDp < 700
    Column(Modifier.fillMaxSize().background(DegradadoMarca).statusBarsPadding().navigationBarsPadding()
        .verticalScroll(rememberScrollState()).padding(horizontal = 26.dp, vertical = 18.dp),
        horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
        LogoFoundia(claro = true, modifier = Modifier.padding(top = 4.dp))
        EscenaFindi(Modifier.fillMaxWidth().height(if (pequena) 190.dp else 250.dp), conSaludo = false)
        Text("Bienvenido a\nFoundIA", color = Color.White, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Text("Recupera y encuentra tus objetos en TECSUP con la ayuda de FINDI.",
            color = Color(0xFFDCE9FF), style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center)
        EstadoAccion(accion)
        Button(onClick = { vm.completar("perfil") }, enabled = !accion.ocupada,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = RoundedCornerShape(18.dp),
            colors = ButtonDefaults.buttonColors(containerColor = AmarilloFindi, contentColor = MarinoFindi)) {
            Icon(Simbolos.Perfil, null, Modifier.size(19.dp)); Spacer(Modifier.width(10.dp)); Text("Iniciar sesión")
        }
        OutlinedButton(onClick = { vm.completar("registro") }, enabled = !accion.ocupada,
            modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), shape = RoundedCornerShape(18.dp),
            border = BorderStroke(1.dp, Color.White.copy(alpha = .65f)), colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White)) {
            Text("Crear cuenta")
        }
        TextButton(onClick = { vm.completar() }, enabled = !accion.ocupada) { Text("Explorar sin cuenta", color = Color.White) }
        Text("COMUNIDAD TECSUP", color = Color(0xFFBFD8FF), style = MaterialTheme.typography.labelSmall)
    }
}
