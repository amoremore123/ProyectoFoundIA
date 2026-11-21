package com.proyectointegrador.movil.navigation

import android.net.Uri
import android.app.Activity
import android.content.ContextWrapper
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
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
import com.proyectointegrador.movil.domain.DestinoPush
import com.proyectointegrador.movil.domain.destinoDeEnlace
import com.proyectointegrador.movil.ui.viewmodel.SesionViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.proyectointegrador.movil.ui.screens.*
import com.proyectointegrador.movil.ui.screens.avisos.*
import com.proyectointegrador.movil.ui.screens.chat.*
import com.proyectointegrador.movil.ui.screens.coincidencias.*
import com.proyectointegrador.movil.ui.screens.cuenta.*
import com.proyectointegrador.movil.ui.screens.publicaciones.*

@Composable fun AppNavHost(enlace:Uri?=null, abrirAvisos:Boolean=false, consumirAvisos:()->Unit={}, consumirEnlace:()->Unit={}, enlacePush:String?=null, consumirPush:()->Unit={}) {
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
            controlador.isAppearanceLightStatusBars=!(fondoOscuro||ruta in listOf("bienvenida","inicio","asistente")||(!sesion.autenticado&&(ruta in listOf("perfil","avisos")||ruta?.startsWith("publicar?")==true)))
            controlador.isAppearanceLightNavigationBars=!fondoOscuro
        }}
        var recuperacion by remember {mutableStateOf<String?>(null)}
        var aperturaRecuperacion by remember {mutableLongStateOf(0L)}
        LaunchedEffect(enlace) { if(enlace!=null) {
            recuperacion=tokenDeRecuperacion(enlace.toString(),BuildConfig.WEB_URL)
            aperturaRecuperacion++
            // Cada apertura vuelve a validar el enlace, incluso si se abre de nuevo el mismo.
            nav.navigate("nueva-clave") {popUpTo(nav.graph.startDestinationId){inclusive=false};launchSingleTop=true};consumirEnlace()
        } }
        fun abrir(ruta:String) {nav.navigate(ruta) {launchSingleTop=true}}
        LaunchedEffect(abrirAvisos) { if (abrirAvisos) { abrir(if (sesion.autenticado) "avisos" else "perfil"); consumirAvisos() } }
        // H57: tocar un aviso push abre la pantalla del enlace; sin sesión se pide iniciar sesión primero.
        LaunchedEffect(enlacePush) { if (enlacePush != null) {
            if (!sesion.autenticado) abrir("perfil") else when (val destino = destinoDeEnlace(enlacePush)) {
                is DestinoPush.Detalle -> abrir("detalle/${destino.objetoId}")
                is DestinoPush.Conversacion -> abrir("conversacion/${destino.id}")
                DestinoPush.Avisos -> abrir("avisos")
            }
            consumirPush()
        } }
        val volver:()->Unit={nav.popBackStack();Unit}
        LaunchedEffect(evento){evento?.let {experiencia.consumirEvento();nav.navigate(it){popUpTo(nav.graph.startDestinationId){inclusive=true};launchSingleTop=true}}}
        LaunchedEffect(ruta,sesion.revision){if(ruta=="inicio"||ruta=="avisos")comunidad.recargar()}
        val etiquetasCompactas=LocalConfiguration.current.screenWidthDp<360&&LocalDensity.current.fontScale>1.1f
        val pestanas=listOf("inicio" to "Inicio","propios" to "Reportes","asistente" to "FINDI","buscar" to "Buscar","perfil" to "Perfil")
        val principal=ruta in listOf("inicio","buscar","avisos","perfil","propios") || ruta?.startsWith("publicar?")==true
        // Con el teclado abierto se oculta la barra inferior y el relleno del Scaffold se consume: antes imePadding() contaba dos veces su altura.
        val tecladoVisible=WindowInsets.ime.getBottom(LocalDensity.current)>0
        Scaffold(contentWindowInsets=WindowInsets(0),bottomBar={if(principal&&!tecladoVisible) Surface(shadowElevation=12.dp,color=MaterialTheme.colorScheme.surface,shape=RoundedCornerShape(topStart=24.dp,topEnd=24.dp)) {
            NavigationBar(containerColor=androidx.compose.ui.graphics.Color.Transparent,tonalElevation=0.dp) {
                pestanas.forEachIndexed {indice,(destino,texto)->
                    val iconos=listOf(Simbolos.Inicio,Simbolos.Ajustes,Simbolos.Chat,Simbolos.Buscar,Simbolos.Perfil)
                    NavigationBarItem(alwaysShowLabel=!etiquetasCompactas,selected=if(destino=="publicar")ruta?.startsWith("publicar")==true else ruta==destino,
                        onClick={nav.navigate(if(destino=="propios"&&!sesion.autenticado)"perfil" else destino){popUpTo(nav.graph.startDestinationId){saveState=true};restoreState=destino!="asistente";launchSingleTop=true}},
                        icon={if(destino=="asistente") Surface(color=AmarilloFindi,shape=RoundedCornerShape(50)){Icon(iconos[indice],texto,Modifier.padding(9.dp).size(23.dp),tint=MarinoFindi)}
                            else if(destino=="avisos"&&actividad.sinLeer>0) BadgedBox(badge={Badge{Text(actividad.sinLeer.toString())}}){Icon(iconos[indice],texto,Modifier.size(23.dp))}
                            else Icon(iconos[indice],texto,Modifier.size(23.dp))},label={Text(texto,style=MaterialTheme.typography.labelSmall,maxLines=2,textAlign=androidx.compose.ui.text.style.TextAlign.Center)})
                }
            }
        }}) { padding ->
            CompositionLocalProvider(LocalAbrirRuta provides {destino:String->abrir(destino)},LocalAbrirWeb provides {clave:String,parametro:String?->abrir("web/"+clave+(if(parametro.isNullOrBlank()) "" else "?p="+Uri.encode(parametro)))}) {
            NavHost(navController=nav,startDestination=if(!sesion.autenticado&&!ajustes.introduccionCompleta)"bienvenida" else if(sesion.aviso!=null)"perfil" else "inicio",modifier=Modifier.padding(padding).consumeWindowInsets(padding),
                enterTransition={fadeIn()},exitTransition={fadeOut()}) {
                composable("bienvenida"){BienvenidaScreen(experiencia,::abrir)}
                composable("inicio") {InicioScreen(onObjetoClick={abrir("detalle/$it")},abrir=::abrir,comunidad=actividad,autenticado=sesion.autenticado)}
                composable("asistente") {AsistenteScreen(volver,abrir={destino->abrir(if(destino=="propios"&&!sesion.autenticado)"perfil" else destino)},autenticado=sesion.autenticado)}
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
                composable("nueva-clave") {NuevaClaveScreen(recuperacion,volver,entrar={recuperacion=null;abrir("perfil")},apertura=aperturaRecuperacion)}
                composable("detalle/{id}",arguments=listOf(navArgument("id"){type=NavType.LongType})) {
                    DetalleScreen(objetoId=it.arguments!!.getLong("id"),onBack=volver,onObjetoClick={abrir("detalle/$it")},
                        onComparar={abrir("pareja/$it")},onReportar={abrir("reportar/$it")},onAcceso={abrir("perfil")},
                        onReclamo={if (sesion.autenticado) abrir("reclamo-custodia/$it") else abrir("perfil")})
                }
                composable("pareja/{id}") {ParejaScreen(it.arguments?.getString("id").orEmpty(),volver,propios={
                    if(sesion.autenticado) abrir("propios") else abrir("perfil")
                })}
                composable("publicar?tipo={tipo}&categoria={categoria}&ubicacion={ubicacion}",arguments=listOf(
                    navArgument("tipo"){defaultValue="PERDIDO"},
                    navArgument("categoria"){type=NavType.LongType;defaultValue=0L},
                    navArgument("ubicacion"){defaultValue=""}
                )){entradaPublicar->
                    if(sesion.autenticado)PublicarScreen(tipoInicial=entradaPublicar.arguments?.getString("tipo").orEmpty(),
                        categoriaInicial=entradaPublicar.arguments?.getLong("categoria") ?: 0L,
                        ubicacionInicial=entradaPublicar.arguments?.getString("ubicacion").orEmpty(),onPublicado={abrir("detalle/$it")})
                    else AccesoScreen({abrir("registro")},{abrir("verificar/${Uri.encode(it.ifEmpty {"_"})}")},{abrir("recuperar")})
                }
                composable("avisos") {if(sesion.autenticado)AvisosScreen(volver,abrir=::abrir) else AccesoScreen({abrir("registro")},{abrir("verificar/${Uri.encode(it.ifEmpty {"_"})}")},{abrir("recuperar")})}
                composable("conversaciones") {if(sesion.autenticado)ConversacionesScreen(volver,::abrir) else AccesoScreen({abrir("registro")},{abrir("verificar/${Uri.encode(it.ifEmpty {"_"})}")},{abrir("recuperar")})}
                composable("conversacion/{id}",arguments=listOf(navArgument("id"){type=NavType.LongType})) {
                    if(sesion.autenticado)ChatScreen(it.arguments!!.getLong("id"),volver) else AccesoScreen({abrir("registro")},{abrir("verificar/${Uri.encode(it.ifEmpty {"_"})}")},{abrir("recuperar")})
                }
                composable("apariencia"){AparienciaScreen(volver,experiencia)}
                composable("privacidad"){PrivacidadScreen(volver)}
                composable("web/{clave}?p={p}",arguments=listOf(navArgument("clave"){type=NavType.StringType},navArgument("p"){type=NavType.StringType;defaultValue=""})) {entrada->
                    val clave=entrada.arguments?.getString("clave").orEmpty()
                    val url=com.proyectointegrador.movil.domain.urlPaginaWeb(BuildConfig.WEB_URL,clave,entrada.arguments?.getString("p")?.takeIf {it.isNotBlank()})
                    val titulo=com.proyectointegrador.movil.domain.PAGINAS_WEB_PERMITIDAS[clave]
                    if(url==null||titulo==null) Pagina("Página no disponible",volver){AvisoEstado("Esta página no se puede abrir dentro de la app.",true)}
                    else PaginaWebScreen(titulo,url,BuildConfig.WEB_URL,volver)
                }
                if(sesion.autenticado) {
                    composable("retiro") { RetiroScreen(volver) }
                    composable("reclamo-custodia/{id}", arguments=listOf(navArgument("id"){type=NavType.LongType})) {
                        ReclamoCustodiaScreen(it.arguments!!.getLong("id"), volver)
                    }
                    composable("editar-perfil"){EditarPerfilScreen(volver)}
                    composable("seguridad"){EditarPerfilScreen(volver,seguridad=true)}
                    composable("propios") {PropiosScreen(volver,{abrir("detalle/$it")},{abrir("sugerencias/$it")},{abrir("editar-publicacion/$it")})}
                    composable("editar-publicacion/{id}",arguments=listOf(navArgument("id"){type=NavType.LongType})) {EditarPublicacionScreen(it.arguments!!.getLong("id"),volver)}
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
}
