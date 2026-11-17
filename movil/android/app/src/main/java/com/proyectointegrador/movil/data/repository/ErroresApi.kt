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
        val cuerpo=runCatching { JsonParser.parseString(response()?.errorBody()?.string()).asJsonObject }.getOrNull()
        // Spring responde con "mensaje" y Django con "detail". Del detalle de Django solo se usan los
        // rechazos de negocio en español (400, 404 y 409): QR vencido, usado, de otro objeto, etc.
        fun campo(clave:String)=cuerpo?.get(clave)?.takeIf { it.isJsonPrimitive }?.asString?.takeIf { it.isNotBlank() }
        val texto=campo("mensaje")
        val detalle=if(code() in listOf(400,404,409)) campo("detail") else null
        val mensaje=when(code()) {
            401 -> texto ?: "Tu sesión venció. Vuelve a iniciar sesión."
            403 -> texto ?: "Tu cuenta no tiene permiso para esta acción."
            404 -> detalle ?: "El recurso no está disponible o fue retirado."
            423 -> texto ?: "La cuenta está bloqueada temporalmente. Intenta más tarde."
            429 -> texto ?: "Espera antes de solicitar otro envío."
            else -> texto ?: detalle ?: "No se pudo completar la operación. Reintenta."
        }
        return ErrorDatos(mensaje,code(),response()?.headers()?.get("Retry-After")?.toIntOrNull()?.coerceIn(1,3600))
    }
    return ErrorDatos("No se pudo completar la operación. Reintenta.")
}

suspend fun <T> llamadaApi(bloque: suspend () -> T): T = try { bloque() }
catch(e: Exception) { throw e.comoErrorDatos() }
