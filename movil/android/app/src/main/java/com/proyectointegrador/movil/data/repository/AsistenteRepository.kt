package com.proyectointegrador.movil.data.repository

import com.proyectointegrador.movil.data.remote.ApiService
import com.proyectointegrador.movil.model.*
import java.text.Normalizer
import java.util.Locale

interface AsistenteRepository {
    suspend fun responder(mensaje: String): RespuestaFindi
}

class ApiAsistenteRepository(private val api: ApiService) : AsistenteRepository {
    override suspend fun responder(mensaje: String): RespuestaFindi = llamadaApi {
        val r = api.interpretarFindi(ConsultaFindi(mensaje))
        val texto = Normalizer.normalize(mensaje.lowercase(Locale.ROOT), Normalizer.Form.NFD).replace(Regex("\\p{M}"), "")
        when {
            r.intencion == "mis_reportes" || texto.contains("mis reportes") || texto.contains("mis publicaciones") ->
                RespuestaFindi(r.resumenPropio ?: "En Mis reportes puedes consultar tus publicaciones y su estado. Inicia sesión si aún no lo has hecho.", accion = AccionFindi("Consultar mis reportes", "propios"))
            r.intencion == "como_funciona" || texto.contains("como funciona") -> RespuestaFindi(PROCESO_FINDI)
            r.intencion == "publicar" -> RespuestaFindi("Reporta qué objeto perdiste o encontraste, dónde y cuándo. Una foto y los detalles ayudan a reconocerlo.", accion = AccionFindi("Reportar un objeto", "publicar?tipo=PERDIDO"))
            r.intencion == "buscar" -> {
                val objetos = api.buscarObjetos(q = r.palabras?.firstOrNull()?.takeIf { it.isNotBlank() },
                    tipo = r.tipoBuscado?.takeIf { it in listOf("PERDIDO", "ENCONTRADO") }, categoriaId = r.categoria?.id,
                    ubicacion = r.ubicacion?.takeIf { it.isNotBlank() })
                    .filter { it.estado !in listOf("OCULTO", "ELIMINADO") }.take(3)
                if (objetos.isEmpty()) RespuestaFindi("No encontré publicaciones con esas características. Prueba otra descripción o publica tu reporte.",
                    accion = AccionFindi("Reportar mi objeto", "publicar?tipo=PERDIDO"), fuente = r.fuente)
                else RespuestaFindi("Estas publicaciones podrían ayudarte. Abre sus detalles y comprueba si alguna corresponde a tu objeto.", objetos,
                    AccionFindi("Ver más publicaciones", "buscar"), r.fuente)
            }
            r.intencion == "gracias" -> RespuestaFindi("¡Para eso estoy! Si necesitas otra búsqueda, cuéntame.")
            else -> RespuestaFindi("Puedo ayudarte a buscar un objeto, reportarlo y consultar tus reportes. Cuéntame qué perdiste y cómo es.")
        }
    }
}

const val PROCESO_FINDI = "Reporta lo que perdiste o encontraste. Revisa las posibles coincidencias, verifica un detalle que solo el dueño conozca y coordina la entrega en un lugar seguro del campus. Confirma la recuperación cuando tengas tu objeto."
