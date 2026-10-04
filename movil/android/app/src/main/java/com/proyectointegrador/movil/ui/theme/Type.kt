package com.proyectointegrador.movil.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

private val default = Typography()

val FoundIATypography = Typography(
    // H1 hero 28-32px
    headlineMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 28.sp
    ),
    // H2 seccion 20px
    titleLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 20.sp
    ),
    // H3 tarjeta 16px
    titleMedium = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.Bold,
        fontSize = 16.sp
    ),
    // Cuerpo 15-16px
    bodyLarge = default.bodyLarge.copy(
        fontFamily = FontFamily.SansSerif,
        fontSize = 16.sp,
        color = TextMuted
    ),
    bodyMedium = default.bodyMedium.copy(
        fontFamily = FontFamily.SansSerif,
        fontSize = 15.sp,
        color = TextMuted
    ),
    // Secundario 13px
    bodySmall = default.bodySmall.copy(
        fontFamily = FontFamily.SansSerif,
        fontSize = 13.sp,
        color = TextSoft
    ),
    labelLarge = TextStyle(
        fontFamily = FontFamily.SansSerif,
        fontWeight = FontWeight.SemiBold,
        fontSize = 14.sp
    ),
    labelSmall = default.labelSmall.copy(
        fontFamily = FontFamily.SansSerif,
        fontSize = 11.sp
    )
)
