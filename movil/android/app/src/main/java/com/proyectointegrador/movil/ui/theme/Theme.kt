package com.proyectointegrador.movil.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

private val Light=lightColorScheme(
 primary=Color(0xFF255BE8),onPrimary=Color.White,primaryContainer=Color(0xFFE8EFFF),onPrimaryContainer=Color(0xFF1746B3),
 secondary=Color(0xFF006B8A),onSecondary=Color.White,secondaryContainer=Color(0xFFDDF5FC),onSecondaryContainer=Color(0xFF00546E),
 tertiary=Color(0xFF7154CE),tertiaryContainer=Color(0xFFF0EAFE),onTertiaryContainer=Color(0xFF5935A5),
 background=Color(0xFFF3F6FC),onBackground=Color(0xFF14203A),surface=Color.White,onSurface=Color(0xFF14203A),
 surfaceVariant=Color(0xFFEAF0F9),onSurfaceVariant=Color(0xFF53627B),outline=Color(0xFF8190A7),outlineVariant=Color(0xFFDCE4F0),
 error=Color(0xFFB9253B),errorContainer=Color(0xFFFFE9ED),onErrorContainer=Color(0xFF8B1528))
private val Dark=darkColorScheme(
 primary=Color(0xFFA9C3FF),onPrimary=Color(0xFF052A7B),primaryContainer=Color(0xFF18386D),onPrimaryContainer=Color(0xFFD8E5FF),
 secondary=Color(0xFF7DDEF8),onSecondary=Color(0xFF003545),secondaryContainer=Color(0xFF104458),onSecondaryContainer=Color(0xFFB9EFFF),
 tertiary=Color(0xFFD0BCFF),tertiaryContainer=Color(0xFF423263),onTertiaryContainer=Color(0xFFEADFFF),
 background=Color(0xFF0A1223),onBackground=Color(0xFFE7EDF9),surface=Color(0xFF121F34),onSurface=Color(0xFFE7EDF9),
 surfaceVariant=Color(0xFF20304A),onSurfaceVariant=Color(0xFFBAC7DF),outline=Color(0xFF8190A8),outlineVariant=Color(0xFF30415C),
 error=Color(0xFFFFB1BE),errorContainer=Color(0xFF572331),onErrorContainer=Color(0xFFFFD9E0))
@Composable fun FoundIATheme(darkTheme:Boolean=isSystemInDarkTheme(),tema:String="sistema",content:@Composable ()->Unit) {
 val oscuro=when(tema){"oscuro"->true;"claro"->false;else->darkTheme}
 MaterialTheme(colorScheme=if(oscuro)Dark else Light,typography=FoundIATypography,
  shapes=Shapes(extraSmall=RoundedCornerShape(8.dp),small=RoundedCornerShape(12.dp),medium=RoundedCornerShape(18.dp),large=RoundedCornerShape(24.dp),extraLarge=RoundedCornerShape(32.dp)),content=content)
}
