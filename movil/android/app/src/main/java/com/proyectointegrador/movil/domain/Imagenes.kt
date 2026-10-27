package com.proyectointegrador.movil.domain

import com.proyectointegrador.movil.BuildConfig

/** Transformación de presentación; no realiza solicitudes ni accede al almacenamiento. */
fun urlImagen(url:String?):String? {
    if(url.isNullOrBlank()) return null
    if(url.startsWith("http://") || url.startsWith("https://")) return url
    return BuildConfig.API_URL.trimEnd('/')+if(url.startsWith('/')) url else "/$url"
}
fun parejaDeEnlace(enlace:String?):String? = enlace?.let {
    Regex("^/coincidencias/([1-9][0-9]*-[1-9][0-9]*)$").matchEntire(it)?.groupValues?.get(1)
}
