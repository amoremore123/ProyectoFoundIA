package com.proyectointegrador.movil.domain

/** Una copia local nunca acredita una escritura o una consulta nueva al servidor. */
data class Lectura<T>(val datos: T, val desdeCache: Boolean = false, val actualizadoEn: Long = System.currentTimeMillis(), val aviso: String? = null)

data class EstadoSesion(val autenticado: Boolean, val aviso: String? = null)

class ErrorDatos(val mensaje: String, val estadoHttp: Int? = null, val esperaSegundos: Int? = null) : Exception(mensaje)
