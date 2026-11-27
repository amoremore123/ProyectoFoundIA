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

/** Páginas de FoundIA que se abren dentro de la app con WebView. Solo estas claves, en nuestro propio servidor. */
val PAGINAS_WEB_PERMITIDAS = mapOf(
    "terminos" to "Términos de uso",
    "privacidad" to "Política de privacidad",
    "etiqueta" to "Verificación de etiqueta",
)

/**
 * URL de una página permitida con `embebido=1`, que oculta el menú y el pie de la web.
 * `etiqueta` necesita el token firmado de la etiqueta. Cualquier otra clave devuelve null.
 */
fun urlPaginaWeb(base:String,clave:String,parametro:String?=null):String? = runCatching {
    val uri=URI(base)
    require(uri.scheme in listOf("http","https") && uri.host!=null && uri.userInfo==null && uri.query==null && uri.fragment==null)
    val raiz=base.trimEnd('/')
    when(clave) {
        "terminos","privacidad" -> "$raiz/$clave?embebido=1"
        "etiqueta" -> {
            require(esEtiquetaCustodia(parametro.orEmpty()))
            val token=extraerTokenEtiqueta(parametro.orEmpty())!!
            "$raiz/custodia/verificar?t=${java.net.URLEncoder.encode(token,"UTF-8")}&embebido=1"
        }
        else -> null
    }
}.getOrNull()

/** El WebView solo navega dentro del mismo origen (esquema, servidor y puerto) que la web de FoundIA. */
fun navegacionPermitida(base:String,destino:String):Boolean = runCatching {
    val propio=URI(base);val otro=URI(destino)
    otro.scheme==propio.scheme && otro.host==propio.host && otro.port==propio.port && otro.userInfo==null
}.getOrDefault(false)
