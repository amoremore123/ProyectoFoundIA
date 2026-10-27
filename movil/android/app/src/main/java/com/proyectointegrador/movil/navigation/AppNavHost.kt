package com.proyectointegrador.movil.navigation

import android.net.Uri
import android.app.Activity
import android.content.ContextWrapper
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.graphics.luminance
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.animation.*
import androidx.compose.ui.unit.dp
import com.proyectointegrador.movil.ui.components.*
import com.proyectointegrador.movil.ui.viewmodel.*
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
    val experiencia:ExperienciaViewModel=viewModel(factory=fabrica())
    val ajustes by experiencia.estado.collectAsStateWithLifecycle()
    val evento by experiencia.evento.collectAsStateWithLifecycle()
    val comunidad:ComunidadViewModel=viewModel(factory=fabrica())
    val actividad by comunidad.estado.collectAsStateWithLifecycle()
    if(!ajustes.cargada){Surface(Modifier.fillMaxSize()){Box(Modifier.padding(32.dp)){LogoFoundia()}};return}
    // Cambiar de sesión destruye las pantallas y los datos de la cuenta anterior.
    key(sesion.revision,sesion.autenticado) {
        val nav=rememberNavController()
        val entrada by nav.currentBackStackEntryAsState()
        val ruta=entrada?.destination?.route
        val contexto=LocalContext.current
        val fondoOscuro=MaterialTheme.colorScheme.background.luminance()<.25f
        SideEffect { generateSequence(contexto){(it as? ContextWrapper)?.baseContext}.filterIsInstance<Activity>().firstOrNull()?.let {activity->
            val controlador=WindowInsetsControllerCompat(activity.window,activity.window.decorView)
            controlador.isAppearanceLightStatusBars=!(fondoOscuro||ruta=="bienvenida"||(!sesion.autenticado&&ruta in listOf("perfil","avisos","publicar?tipo={tipo}")))
            controlador.isAppearanceLightNavigationBars=!fondoOscuro
        }}
        var recuperacion by remember {mutableStateOf<String?>(null)}
        LaunchedEffect(enlace) { if(enlace!=null) {
            recuperacion=tokenDeRecuperacion(enlace.toString(),BuildConfig.WEB_URL)
            nav.navigate("nueva-clave") {launchSingleTop=true};consumirEnlace()
        } }
        fun abrir(ruta:String) {nav.navigate(ruta) {launchSingleTop=true}}
        val volver:()->Unit={nav.popBackStack();Unit}
        LaunchedEffect(evento){evento?.let {experiencia.consumirEvento();nav.navigate(it){popUpTo(nav.graph.startDestinationId){inclusive=true};launchSingleTop=true}}}
        LaunchedEffect(ruta,sesion.revision){if(ruta=="inicio"||ruta=="avisos")comunidad.recargar()}
        val pestanas=listOf("inicio" to "Inicio","buscar" to "Buscar","publicar" to "Publicar","avisos" to "Avisos","perfil" to "Perfil")
        val principal=ruta in listOf("inicio","buscar","publicar?tipo={tipo}","avisos","perfil")
        Scaffold(contentWindowInsets=WindowInsets(0),bottomBar={if(principal) Surface(shadowElevation=12.dp,color=MaterialTheme.colorScheme.surface,shape=RoundedCornerShape(topStart=24.dp,topEnd=24.dp)) {
            NavigationBar(containerColor=androidx.compose.ui.graphics.Color.Transparent,tonalElevation=0.dp) {
                pestanas.forEachIndexed {indice,(destino,texto)->
                    val iconos=listOf(Simbolos.Inicio,Simbolos.Buscar,Simbolos.Publicar,Simbolos.Avisos,Simbolos.Perfil)
                    NavigationBarItem(selected=if(destino=="publicar")ruta?.startsWith("publicar")==true else ruta==destino,
                        onClick={nav.navigate(if(destino=="publicar")"publicar?tipo=PERDIDO" else destino){popUpTo(nav.graph.startDestinationId){saveState=true};restoreState=true;launchSingleTop=true}},
                        icon={if(destino=="publicar") Surface(color=MaterialTheme.colorScheme.primary,shape=RoundedCornerShape(13.dp)){Icon(iconos[indice],null,Modifier.padding(9.dp).size(23.dp),tint=MaterialTheme.colorScheme.onPrimary)}
                            else if(destino=="avisos"&&actividad.sinLeer>0) BadgedBox(badge={Badge{Text(actividad.sinLeer.toString())}}){Icon(iconos[indice],null,Modifier.size(23.dp))}
                            else Icon(iconos[indice],null,Modifier.size(23.dp))},label={Text(texto,style=MaterialTheme.typography.labelSmall,maxLines=1,softWrap=false,modifier=Modifier.wrapContentWidth(unbounded=true))})
                }
            }
        }}) { padding ->
            NavHost(navController=nav,startDestination=if(!sesion.autenticado&&!ajustes.introduccionCompleta)"bienvenida" else if(sesion.aviso!=null)"perfil" else "inicio",modifier=Modifier.padding(padding),
                enterTransition={fadeIn()},exitTransition={fadeOut()}) {
                composable("bienvenida"){BienvenidaScreen(experiencia,::abrir)}
                composable("inicio") {InicioScreen(onObjetoClick={abrir("detalle/$it")},abrir=::abrir,comunidad=actividad)}
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
                composable("publicar?tipo={tipo}",arguments=listOf(navArgument("tipo"){defaultValue="PERDIDO"})){entradaPublicar->
                    if(sesion.autenticado)PublicarScreen(tipoInicial=entradaPublicar.arguments?.getString("tipo").orEmpty(),onPublicado={abrir("detalle/$it")})
                    else AccesoScreen({abrir("registro")},{abrir("verificar/${Uri.encode(it.ifEmpty {"_"})}")},{abrir("recuperar")})
                }
                composable("avisos") {if(sesion.autenticado)AvisosScreen(volver,abrir={abrir("pareja/$it")}) else AccesoScreen({abrir("registro")},{abrir("verificar/${Uri.encode(it.ifEmpty {"_"})}")},{abrir("recuperar")})}
                composable("apariencia"){AparienciaScreen(volver,experiencia)}
                composable("privacidad"){PrivacidadScreen(volver)}
                if(sesion.autenticado) {
                    composable("editar-perfil"){EditarPerfilScreen(volver)}
                    composable("seguridad"){EditarPerfilScreen(volver,seguridad=true)}
                    composable("propios") {PropiosScreen(volver,{abrir("detalle/$it")},{abrir("sugerencias/$it")})}
                    composable("sugerencias/{id}",arguments=listOf(navArgument("id"){type=NavType.LongType})) {
                        SugerenciasScreen(it.arguments!!.getLong("id"),volver,abrir={abrir("pareja/$it")})
                    }
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
