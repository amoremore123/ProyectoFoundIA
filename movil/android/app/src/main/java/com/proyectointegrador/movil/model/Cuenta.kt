package com.proyectointegrador.movil.model

data class Usuario(val id: Long, val nombre: String, val apellido: String, val correo: String, val rol: String, val estado: String)
data class Acceso(val token: String, val usuario: Usuario)
data class Login(val correo: String, val password: String)
data class Registro(val nombre: String, val apellido: String, val correo: String, val password: String,
    val aceptaTerminos: Boolean, val versionLegal: String)
data class Verificacion(val correo: String, val codigo: String)
data class Correo(val correo: String)
data class Mensaje(val mensaje: String)
data class Documentos(val version: String, val estado: String, val terminos: String, val privacidad: String, val sha256: String)
data class Preferencias(val coincidencias: Boolean = true, val contactos: Boolean = true, val reclamos: Boolean = true)
data class Aviso(val id: Long, val titulo: String, val mensaje: String, val tipo: String, val leida: Boolean,
    val fechaCreacion: String?, val enlace: String?)
data class Pareja(val id: String, val perdido: Objeto, val encontrado: Objeto, val porcentaje: Double,
    val nivel: String, val criterios: Map<String,Int>)
data class EstadoPareja(val id: String, val estado: String, val repetido: Boolean)
data class ConfirmarRecuperacion(val confirmarRecuperacion: Boolean = true)
data class Recuperacion(val id: String, val estado: String, val objetoPerdidoId: Long,
    val estadoObjetoPerdido: String, val fechaRecuperacion: String, val repetido: Boolean)
data class Denuncia(val motivo: String, val descripcion: String?)
data class ResultadoReporte(val id: Long, val objetoId: Long, val estado: String, val repetido: Boolean)
data class ConfirmarEliminacion(val confirmar: Boolean = true)
data class Eliminacion(val id: Long?, val estado: String, val fechaSolicitud: String?, val repetido: Boolean, val informacion: String)
data class Restablecer(val token: String, val password: String)

data class PublicacionRequest(val categoriaId:Long,val nombre:String,val descripcion:String,val fechaObjeto:String,
    val tipo:String,val ubicacion:String,val latitud:Double?=null,val longitud:Double?=null)

data class PerfilRequest(val nombre:String,val apellido:String,val password:String?=null,val passwordActual:String?=null)

data class ContactoRequest(val objetoPerdidoId:Long,val objetoEncontradoId:Long)
data class ContactoResultado(val id:Long,val estado:String,val repetido:Boolean)
