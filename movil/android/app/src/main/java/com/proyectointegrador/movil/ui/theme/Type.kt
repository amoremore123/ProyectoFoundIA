package com.proyectointegrador.movil.ui.theme
import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
private fun estilo(tamano:Int,alto:Int,peso:FontWeight=FontWeight.Normal,espacio:Float=0f)=TextStyle(fontFamily=FontFamily.SansSerif,fontWeight=peso,fontSize=tamano.sp,lineHeight=alto.sp,letterSpacing=espacio.sp)
val FoundIATypography=Typography(
 displaySmall=estilo(36,42,FontWeight.Bold,-1f),headlineLarge=estilo(32,38,FontWeight.Bold,-0.8f),
 headlineMedium=estilo(28,34,FontWeight.Bold,-0.7f),headlineSmall=estilo(24,30,FontWeight.Bold,-0.5f),
 titleLarge=estilo(21,27,FontWeight.Bold,-0.4f),titleMedium=estilo(16,22,FontWeight.SemiBold,-0.2f),titleSmall=estilo(14,20,FontWeight.SemiBold),
 bodyLarge=estilo(16,24),bodyMedium=estilo(14,21),bodySmall=estilo(12,18),
 labelLarge=estilo(14,20,FontWeight.SemiBold),labelMedium=estilo(12,16,FontWeight.SemiBold),labelSmall=estilo(11,15,FontWeight.SemiBold,0.3f))
