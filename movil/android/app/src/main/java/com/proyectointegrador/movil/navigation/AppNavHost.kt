package com.proyectointegrador.movil.navigation

import android.net.Uri
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.navigation.NavType
import androidx.navigation.compose.*
import androidx.navigation.navArgument
import com.proyectointegrador.movil.BuildConfig
import com.proyectointegrador.movil.network.Sesion
import com.proyectointegrador.movil.ui.screens.*
import com.proyectointegrador.movil.ui.sprint.*

@Composable fun AppNavHost(enlace:Uri?=null,consumirEnlace:()->Unit={}) {
    val token by Sesion.token.collectAsState()
    // Cambiar de sesión destruye las pantallas y los datos de la cuenta anterior.
    key(token) {
        val nav=rememberNavController()
        val entrada by nav.currentBackStackEntryAsState()
        val ruta=entrada?.destination?.route
        var recuperacion by remember {mutableStateOf<String?>(null)}
        LaunchedEffect(enlace) { if(enlace!=null) {
            recuperacion=tokenDeRecuperacion(enlace.toString(),BuildConfig.WEB_URL)
            nav.navigate("nueva-clave") {launchSingleTop=true};consumirEnlace()
        } }
        fun abrir(ruta:String) {nav.navigate(ruta) {launchSingleTop=true}}
        val volver:()->Unit={nav.popBackStack();Unit}
        Scaffold(bottomBar={if(ruta in listOf("inicio","buscar","perfil")) NavigationBar {
            listOf("inicio" to "Inicio","buscar" to "Buscar","perfil" to "Cuenta").forEach { (destino,texto) ->
                NavigationBarItem(selected=ruta==destino,onClick={abrir(destino)},icon={Text(texto.take(1))},label={Text(texto)})
            }
        }}) { padding ->
            NavHost(navController=nav,startDestination=if(token==null) "inicio" else "perfil",modifier=Modifier.padding(padding)) {
                composable("inicio") {InicioScreen(onObjetoClick={abrir("detalle/$it")})}
                composable("buscar") {BuscarScreen(onObjetoClick={abrir("detalle/$it")})}
                composable("perfil") {
                    if(token==null) AccesoScreen({abrir("registro")},{abrir("verificar/${Uri.encode(it.ifEmpty {"_"})}")},{abrir("recuperar")})
                    else CuentaScreen {abrir(it)}
                }
                composable("registro") {RegistroScreen(volver) {abrir("verificar/${Uri.encode(it)}")}}
                composable("verificar/{correo}",arguments=listOf(navArgument("correo"){type=NavType.StringType})) {
                    VerificarScreen(it.arguments?.getString("correo").orEmpty().takeUnless {it=="_"}.orEmpty(),volver)
                }
                composable("recuperar") {RecuperarScreen(volver)}
                composable("nueva-clave") {NuevaClaveScreen(recuperacion,volver) {recuperacion=null;abrir("perfil")}}
                composable("detalle/{id}",arguments=listOf(navArgument("id"){type=NavType.LongType})) {
                    DetalleScreen(objetoId=it.arguments!!.getLong("id"),onBack=volver,onObjetoClick={abrir("detalle/$it")},
                        onComparar={abrir("pareja/$it")},onReportar={abrir("reportar/$it")},onAcceso={abrir("perfil")})
                }
                composable("pareja/{id}") {ParejaScreen(it.arguments?.getString("id").orEmpty(),volver) {
                    if(token!=null) abrir("propios") else abrir("perfil")
                }}
                if(token!=null) {
                    composable("propios") {PropiosScreen(volver,{abrir("detalle/$it")},{abrir("sugerencias/$it")})}
                    composable("sugerencias/{id}",arguments=listOf(navArgument("id"){type=NavType.LongType})) {
                        SugerenciasScreen(it.arguments!!.getLong("id"),volver) {abrir("pareja/$it")}
                    }
                    composable("avisos") {AvisosScreen(volver) {abrir("pareja/$it")}}
                    composable("preferencias") {PreferenciasScreen(volver)}
                    composable("eliminacion") {EliminacionScreen(volver)}
                    composable("reportar/{id}",arguments=listOf(navArgument("id"){type=NavType.LongType})) {
                        ReporteScreen(it.arguments!!.getLong("id"),volver)
                    }
                }
            }
        }
    }
}
