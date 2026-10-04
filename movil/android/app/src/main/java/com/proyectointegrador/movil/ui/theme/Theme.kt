package com.proyectointegrador.movil.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable

private val FoundIALightColors = lightColorScheme(
    primary = Primary,
    onPrimary = SurfaceWhite,
    primaryContainer = Primary50,
    onPrimaryContainer = Primary700,
    secondary = Primary700,
    background = Background,
    onBackground = TextTitle,
    surface = SurfaceWhite,
    onSurface = TextTitle,
    surfaceVariant = SurfaceWhite,
    onSurfaceVariant = TextMuted,
    outline = Border,
    error = Danger
)

@Composable
fun FoundIATheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit
) {
    // La guia define una unica paleta clara para toda la plataforma.
    MaterialTheme(
        colorScheme = FoundIALightColors,
        typography = FoundIATypography,
        content = content
    )
}
