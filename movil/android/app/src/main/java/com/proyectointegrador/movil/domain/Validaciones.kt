package com.proyectointegrador.movil.domain

fun correoValido(valor:String)=valor.trim().length<=150 && Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$").matches(valor.trim())
fun passwordValida(valor:String)=valor.length>=8 && valor.toByteArray(Charsets.UTF_8).size<=72 &&
    valor.any(Char::isUpperCase) && valor.any(Char::isLowerCase) && valor.any(Char::isDigit) && valor.any {!it.isLetterOrDigit()}
