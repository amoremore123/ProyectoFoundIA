package com.proyectointegrador.movil.domain

import com.proyectointegrador.movil.BuildConfig

/** Transformación de presentación; no realiza solicitudes ni accede al almacenamiento. */
fun urlImagen(url:String?):String? {
    if(url.isNullOrBlank()) return null
    if(url.startsWith("http://") || url.startsWith("https://")) return url
    return BuildConfig.API_URL.trimEnd('/')+if(url.startsWith('/')) url else "/$url"
}
fun rutaDeAviso(enlace: String?, tipo: String? = null): String? {
    parejaDeEnlace(enlace)?.let { return "pareja/$it" }
    val id = enlace?.let { Regex("^/objetos?/([1-9][0-9]*)$").matchEntire(it) }
        ?.groupValues?.get(1)?.toLongOrNull()
    return id?.let { if (tipo == "RECLAMO") "reclamo-custodia/$it" else "detalle/$it" }
}

fun parejaDeEnlace(enlace:String?):String? = enlace?.let {
    Regex("^/coincidencias/([1-9][0-9]*-[1-9][0-9]*)$").matchEntire(it)?.groupValues?.get(1)
}
