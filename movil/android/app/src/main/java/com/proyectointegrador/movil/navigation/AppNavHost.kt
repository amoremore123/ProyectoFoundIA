package com.proyectointegrador.movil.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.proyectointegrador.movil.ui.screens.BuscarScreen
import com.proyectointegrador.movil.ui.screens.DetalleScreen
import com.proyectointegrador.movil.ui.screens.InicioScreen
import com.proyectointegrador.movil.ui.screens.PerfilScreen
import com.proyectointegrador.movil.ui.screens.PublicarScreen
import com.proyectointegrador.movil.ui.theme.Background
import com.proyectointegrador.movil.ui.theme.Primary
import com.proyectointegrador.movil.ui.theme.SurfaceWhite
import com.proyectointegrador.movil.ui.theme.TextSoft

private data class Destino(
    val ruta: String,
    val etiqueta: String,
    val icono: ImageVector
)

private val DESTINOS = listOf(
    Destino("inicio", "Inicio", Icons.Default.Home),
    Destino("buscar", "Buscar", Icons.Default.Search),
    Destino("publicar", "Publicar", Icons.Default.Add),
    Destino("perfil", "Perfil", Icons.Default.Person)
)

@Composable
fun AppNavHost() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val rutaActual = backStackEntry?.destination?.route
    val esSuperior = DESTINOS.any { it.ruta == rutaActual }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            if (esSuperior) {
                NavigationBar(containerColor = SurfaceWhite) {
                    DESTINOS.forEach { destino ->
                        NavigationBarItem(
                            selected = rutaActual == destino.ruta,
                            onClick = {
                                navController.navigate(destino.ruta) {
                                    popUpTo("inicio") { saveState = true }
                                    launchSingleTop = true
                                    restoreState = true
                                }
                            },
                            icon = { Icon(destino.icono, contentDescription = destino.etiqueta) },
                            label = { Text(destino.etiqueta) },
                            colors = NavigationBarItemDefaults.colors(
                                selectedIconColor = Primary,
                                selectedTextColor = Primary,
                                unselectedIconColor = TextSoft,
                                unselectedTextColor = TextSoft,
                                indicatorColor = MaterialTheme.colorScheme.primaryContainer
                            )
                        )
                    }
                }
            }
        }
    ) { padding ->
        NavHost(
            navController = navController,
            startDestination = "inicio",
            modifier = Modifier.padding(padding)
        ) {
            composable("inicio") {
                InicioScreen(onObjetoClick = { navController.navigate("detalle/$it") })
            }
            composable("buscar") {
                BuscarScreen(onObjetoClick = { navController.navigate("detalle/$it") })
            }
            composable("publicar") {
                PublicarScreen()
            }
            composable("perfil") {
                PerfilScreen()
            }
            composable(
                route = "detalle/{id}",
                arguments = listOf(navArgument("id") { type = NavType.LongType })
            ) { entry ->
                val id = entry.arguments?.getLong("id") ?: return@composable
                DetalleScreen(
                    objetoId = id,
                    onBack = { navController.popBackStack() },
                    onObjetoClick = { nuevoId -> navController.navigate("detalle/$nuevoId") }
                )
            }
        }
    }
}
