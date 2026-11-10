package com.proyectointegrador.movil.model

data class ConsultaFindi(val mensaje: String)
data class InterpretacionFindi(
    val intencion: String? = null, val tipoBuscado: String? = null,
    val categoria: ObjetoCategoria? = null, val ubicacion: String? = null,
    val palabras: List<String>? = null, val fuente: String? = null,
    val resumenPropio: String? = null
)
data class AccionFindi(val texto: String, val ruta: String)
data class RespuestaFindi(val texto: String, val objetos: List<Objeto> = emptyList(),
    val accion: AccionFindi? = null, val fuente: String? = null)
