package com.proyectointegrador.movil.ui.sprint

import android.content.Intent
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.platform.LocalContext
import com.proyectointegrador.movil.BuildConfig
import com.proyectointegrador.movil.model.Objeto
import com.proyectointegrador.movil.network.*

@Composable fun DetalleAcciones(objeto:Objeto,reportar:(Long)->Unit,entrar:()->Unit) {
    val token by Sesion.token.collectAsState()
    val contexto=LocalContext.current
    val accion=recordarAccion()
    if(token!=null) OutlinedButton(onClick={reportar(objeto.id)}) {Text("Reportar esta publicación")}
    else TextButton(onClick=entrar) {Text("Inicia sesión para reportar")}
    if(objeto.esPerdido) {
        Button(enabled=!accion.ocupada,onClick={accion.ejecutar {
            val vigente=RetrofitClient.api.objetoPorId(objeto.id)
            check(vigente.tipo=="PERDIDO" && vigente.estado in listOf("ACTIVO","RECUPERADO")) {"La publicación ya no está disponible para compartir."}
            val enlace=enlacePublico(BuildConfig.WEB_URL,vigente.id)
            val enviar=Intent(Intent.ACTION_SEND).apply {type="text/plain";putExtra(Intent.EXTRA_TEXT,enlace)}
            contexto.startActivity(Intent.createChooser(enviar,"Compartir publicación de FoundIA"))
        }}) {Text("Compartir publicación")}
        Text("Se abrirá el selector del sistema. Puedes elegir una aplicación o cancelar; FoundIA no confirma el envío.")
    }
    EstadoAccion(accion)
}
