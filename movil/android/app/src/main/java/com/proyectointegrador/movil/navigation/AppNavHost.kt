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
import com.proyectointegrador.movil.di.fabrica
import com.proyectointegrador.movil.domain.tokenDeRecuperacion
import com.proyectointegrador.movil.ui.viewmodel.SesionViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.proyectointegrador.movil.ui.screens.*
import com.proyectointegrador.movil.ui.sprint.*

@Composable fun AppNavHost(enlace:Uri?=null,consumirEnlace:()->Unit={}) {
    val vm:SesionViewModel=viewModel(factory=fabrica())
    val sesion by vm.estado.collectAsStateWithLifecycle()
    // Cambiar de sesión destruye las pantallas y los datos de la cuenta anterior.
    key(sesion.revision,sesion.autenticado) {
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
        Scaffold(bottomBar={if(ruta in listOf("inicio","buscar","publicar","avisos","perfil")) NavigationBar {
            listOf("inicio" to "Inicio","buscar" to "Buscar","publicar" to "Publicar","avisos" to "Avisos","perfil" to "Cuenta").forEach { (destino,texto) ->
                NavigationBarItem(selected=ruta==destino,onClick={abrir(if(!sesion.autenticado && destino in listOf("publicar","avisos")) "perfil" else destino)},icon={Text(texto.take(1))},label={Text(texto)})
            }
        }}) { padding ->
            NavHost(navController=nav,startDestination=if(!sesion.autenticado && sesion.aviso==null) "inicio" else "perfil",modifier=Modifier.padding(padding)) {
                composable("inicio") {InicioScreen(onObjetoClick={abrir("detalle/$it")})}
                composable("buscar") {BuscarScreen(onObjetoClick={abrir("detalle/$it")})}
                composable("perfil") {
                    if(!sesion.autenticado) AccesoScreen({abrir("registro")},{abrir("verificar/${Uri.encode(it.ifEmpty {"_"})}")},{abrir("recuperar")})
                    else CuentaScreen(abrir={abrir(it)})
                }
                composable("registro") {RegistroScreen(volver,verificar={abrir("verificar/${Uri.encode(it)}")})}
                composable("verificar/{correo}",arguments=listOf(navArgument("correo"){type=NavType.StringType})) {
                    VerificarScreen(it.arguments?.getString("correo").orEmpty().takeUnless {it=="_"}.orEmpty(),volver)
                }
                composable("recuperar") {RecuperarScreen(volver)}
                composable("nueva-clave") {NuevaClaveScreen(recuperacion,volver,entrar={recuperacion=null;abrir("perfil")})}
                composable("detalle/{id}",arguments=listOf(navArgument("id"){type=NavType.LongType})) {
                    DetalleScreen(objetoId=it.arguments!!.getLong("id"),onBack=volver,onObjetoClick={abrir("detalle/$it")},
                        onComparar={abrir("pareja/$it")},onReportar={abrir("reportar/$it")},onAcceso={abrir("perfil")})
                }
                composable("pareja/{id}") {ParejaScreen(it.arguments?.getString("id").orEmpty(),volver,propios={
                    if(sesion.autenticado) abrir("propios") else abrir("perfil")
                })}
                if(sesion.autenticado) {
                    composable("publicar") {PublicarScreen(onPublicado={abrir("detalle/$it")})}
                    composable("propios") {PropiosScreen(volver,{abrir("detalle/$it")},{abrir("sugerencias/$it")})}
                    composable("sugerencias/{id}",arguments=listOf(navArgument("id"){type=NavType.LongType})) {
                        SugerenciasScreen(it.arguments!!.getLong("id"),volver,abrir={abrir("pareja/$it")})
                    }
                    composable("avisos") {AvisosScreen(volver,abrir={abrir("pareja/$it")})}
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
