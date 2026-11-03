package com.proyectointegrador.movil.domain

import java.net.URI
import java.net.URLDecoder

/** El enlace público se construye desde un ID, nunca desde el token o el perfil. */
fun enlacePublico(base:String,objetoId:Long):String {
    require(objetoId>0)
    val uri=URI(base)
    require(uri.scheme in listOf("http","https") && uri.host!=null && uri.userInfo==null && uri.query==null && uri.fragment==null)
    return base.trimEnd('/')+"/objeto/"+objetoId
}

fun tokenDeRecuperacion(enlace:String,baseWeb:String):String? = runCatching {
    val uri=URI(enlace);val web=URI(baseWeb)
    val propio=uri.scheme=="foundia" && uri.host=="restablecer" && uri.path.orEmpty() in listOf("","/")
    val webValida=uri.scheme==web.scheme && uri.host==web.host && uri.port==web.port && uri.path=="/restablecer-password"
    require((propio || webValida) && uri.userInfo==null && uri.fragment==null)
    val argumentos=uri.rawQuery.orEmpty().split('&').map { it.split('=',limit=2) }
    require(argumentos.size==1 && argumentos[0].size==2 && argumentos[0][0]=="token")
    URLDecoder.decode(argumentos[0][1],"UTF-8").also {require(it.matches(Regex("[A-Za-z0-9._~-]{16,2048}")))}
}.getOrNull()
