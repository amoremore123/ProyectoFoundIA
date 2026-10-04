package com.proyectointegrador.movil.model

/**
 * ObjetoResponse estandar de la API (seccion 3 de docs/api/endpoints.md).
 * Todos los campos opcionales para tolerar respuestas incompletas de Gson.
 */
data class Objeto(
    val id: Long = 0,
    val nombre: String = "",
    val descripcion: String? = null,
    val ubicacion: String? = null,
    val latitud: Double? = null,
    val longitud: Double? = null,
    val fechaObjeto: String? = null,
    val tipo: String? = null,
    val estado: String? = null,
    val fechaPublicacion: String? = null,
    val categoria: ObjetoCategoria? = null,
    val publicadoPor: PublicadoPor? = null,
    val fotos: List<ObjetoFoto>? = null
) {
    val tipoSeguro: String
        get() = tipo ?: "PERDIDO"

    val esPerdido: Boolean
        get() = tipoSeguro == "PERDIDO"

    val fotosSeguras: List<ObjetoFoto>
        get() = fotos ?: emptyList()
}

data class ObjetoCategoria(
    val id: Long = 0,
    val nombre: String? = null
)

data class PublicadoPor(
    val id: Long = 0,
    val nombre: String? = null,
    val apellido: String? = null
) {
    val nombreCompleto: String
        get() = listOfNotNull(nombre, apellido).joinToString(" ").ifBlank { "—" }
}

data class ObjetoFoto(
    val id: Long = 0,
    val url: String? = null
)
