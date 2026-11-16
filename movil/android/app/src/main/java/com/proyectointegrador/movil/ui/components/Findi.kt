package com.proyectointegrador.movil.ui.components

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.*
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.dp
import com.proyectointegrador.movil.R

val AmarilloFindi = Color(0xFFFFD400)
val AzulFindi = Color(0xFF0866F5)
val MarinoFindi = Color(0xFF14234B)

@Composable fun AvatarFindi(modifier: Modifier = Modifier) {
    Image(painterResource(R.drawable.findi_avatar), "FINDI, asistente de FoundIA",
        modifier.clip(CircleShape).background(MarinoFindi).border(1.dp, Color(0xFF87BCFF), CircleShape), contentScale = ContentScale.Crop)
}

/** La figura y el saludo ocupan columnas diferentes para mantener el rostro libre. */
@Composable fun EscenaFindi(modifier: Modifier = Modifier, conSaludo: Boolean = true, abrir: () -> Unit = {}) {
    Box(modifier) {
        Box(Modifier.align(Alignment.BottomCenter).fillMaxWidth().height(64.dp).background(
            Brush.verticalGradient(listOf(Color.Transparent, AzulFindi.copy(alpha = .28f))), RoundedCornerShape(100)))
        Image(painterResource(R.drawable.findi_hero), "FINDI te ayuda a buscar tus objetos",
            Modifier.align(Alignment.BottomStart).fillMaxHeight().fillMaxWidth(if (conSaludo) .7f else 1f), contentScale = ContentScale.Fit)
        if (conSaludo) Surface(onClick = abrir, modifier = Modifier.align(Alignment.TopEnd).widthIn(max = 128.dp).padding(top = 6.dp),
            shape = RoundedCornerShape(22.dp, 22.dp, 22.dp, 5.dp), color = Color(0xFFF2F6FF),
            border = BorderStroke(1.dp, Color.White.copy(alpha = .7f)), shadowElevation = 8.dp) {
            Column(Modifier.padding(13.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("¡Hola! Soy FINDI", color = MarinoFindi, style = MaterialTheme.typography.titleSmall)
                Text("Tu asistente para reportar, buscar y recuperar objetos.", color = MarinoFindi, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable fun TarjetaFindi(titulo: String, icono: ImageVector, color: Color, modifier: Modifier = Modifier, accion: () -> Unit) {
    Surface(onClick = accion, modifier = modifier, shape = RoundedCornerShape(24.dp),
        color = color.copy(alpha = .13f), border = BorderStroke(1.dp, color.copy(alpha = .22f))) {
        Column(Modifier.padding(17.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Surface(shape = CircleShape, color = color, shadowElevation = 4.dp) {
                Icon(icono, null, Modifier.padding(10.dp).size(23.dp), tint = if (color == AmarilloFindi) MarinoFindi else Color.White)
            }
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(titulo, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
                Icon(Simbolos.Flecha, null, Modifier.size(18.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
    }
}
