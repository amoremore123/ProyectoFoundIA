package com.proyectointegrador.movil.model

/** Respuesta estandar de la API de objetos. */
typealias ObjetoResponse = Objeto

/** Arreglos que devuelve la API. */
typealias ObjetoListResponse = List<ObjetoResponse>
typealias CategoriaListResponse = List<Categoria>
typealias CoincidenciaListResponse = List<CoincidenciaSugerida>

/** GET /api/objetos/{id}/coincidencias */
data class CoincidenciaSugerida(
    val objetoId: Long = 0,
    val nombre: String? = null,
    val tipo: String? = null,
    val categoria: String? = null,
    val ubicacion: String? = null,
    val fechaObjeto: String? = null,
    val porcentaje: Double? = null,
    val nivel: String? = null
)
