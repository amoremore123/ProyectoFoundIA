package com.proyectointegrador.movil.model

data class CoincidenciaSugerida(
    val objetoId: Long = 0,
    val nombre: String? = null,
    val tipo: String? = null,
    val categoria: String? = null,
    val ubicacion: String? = null,
    val fechaObjeto: String? = null,
    val porcentaje: Double? = null,
    val nivel: String? = null,
    val coincidenciaId: String? = null
)
