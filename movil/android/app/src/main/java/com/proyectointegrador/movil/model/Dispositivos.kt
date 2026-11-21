package com.proyectointegrador.movil.model

data class DispositivoRequest(val token: String, val plataforma: String = "ANDROID")
data class Dispositivo(val id: Long, val plataforma: String, val fechaRegistro: String?, val fechaUltimoUso: String?)
data class TokenFirebase(val token: String, val uid: String, val expiraEn: String?)
