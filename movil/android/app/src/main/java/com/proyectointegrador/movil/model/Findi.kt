package com.proyectointegrador.movil.model

data class SolicitudSugerencia(val nombre: String, val descripcion: String, val tipo: String)
data class SugerenciaFindi(val categoria: ObjetoCategoria?, val descripcion: String?, val origen: String)

data class ConsultaFindi(val mensaje: String)
data class InterpretacionFindi(
    val intencion: String? = null, val tipoBuscado: String? = null,
    val categoria: ObjetoCategoria? = null, val ubicacion: String? = null,
    val palabras: List<String>? = null, val fuente: String? = null,
    val resumenPropio: String? = null,
    val tarjetas: List<TarjetaFindi>? = null,
    val acciones: List<PropuestaFindi>? = null
)
data class DatosTarjetaFindi(
    val id: Long?, val tipo: String?, val estado: String?, val ubicacion: String?, val categoria: String?,
    val pendientes: Int?, val aceptados: Int?, val retirados: Int?, val rechazados: Int?, val vigentes: Int?
)
data class TarjetaFindi(val tipo: String, val titulo: String, val datos: DatosTarjetaFindi?, val enlace: String?)
data class DatosAccionFindi(val tipo: String?, val categoriaId: Long?, val ubicacion: String?, val objetoId: Long?)
data class PropuestaFindi(
    val tipo: String, val texto: String, val confirmacion: String?, val destino: String,
    val datos: DatosAccionFindi?, val requiereConfirmacion: Boolean
)

data class AccionFindi(val texto: String, val ruta: String)
data class RespuestaFindi(val texto: String, val objetos: List<Objeto> = emptyList(),
    val accion: AccionFindi? = null, val fuente: String? = null,
    val tarjetas: List<TarjetaFindi> = emptyList(), val propuestas: List<PropuestaFindi> = emptyList())
