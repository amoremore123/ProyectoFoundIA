package com.proyectointegrador.movil.ui.components

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Abre una página de FoundIA dentro de la app (clave y parámetro de [com.proyectointegrador.movil.domain.urlPaginaWeb]).
 * Es null cuando la pantalla vive fuera de la navegación, y entonces se usa el navegador del sistema.
 */
val LocalAbrirWeb = staticCompositionLocalOf<((clave: String, parametro: String?) -> Unit)?> { null }
