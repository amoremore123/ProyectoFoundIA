package com.proyectointegrador.movil.data.repository

import com.google.gson.JsonParser
import com.proyectointegrador.movil.domain.ErrorDatos
import kotlinx.coroutines.CancellationException
import retrofit2.HttpException
import java.io.IOException

fun Throwable.comoErrorDatos(): ErrorDatos {
    if(this is CancellationException) throw this
    if(this is ErrorDatos) return this
    if(this is IOException) return ErrorDatos("No hay conexión con FoundIA. Revisa tu conexión y reintenta.")
    if(this is HttpException) {
        val texto=runCatching { JsonParser.parseString(response()?.errorBody()?.string()).asJsonObject.get("mensaje")?.asString }.getOrNull()
        val mensaje=when(code()) {
            401 -> texto ?: "Tu sesión venció. Vuelve a iniciar sesión."
            403 -> texto ?: "Tu cuenta no tiene permiso para esta acción."
            404 -> "El recurso no está disponible o fue retirado."
            423 -> texto ?: "La cuenta está bloqueada temporalmente. Intenta más tarde."
            429 -> texto ?: "Espera antes de solicitar otro envío."
            else -> texto ?: "No se pudo completar la operación. Reintenta."
        }
        return ErrorDatos(mensaje,code(),response()?.headers()?.get("Retry-After")?.toIntOrNull()?.coerceIn(1,3600))
    }
    return ErrorDatos("No se pudo completar la operación. Reintenta.")
}

suspend fun <T> llamadaApi(bloque: suspend () -> T): T = try { bloque() }
catch(e: Exception) { throw e.comoErrorDatos() }
