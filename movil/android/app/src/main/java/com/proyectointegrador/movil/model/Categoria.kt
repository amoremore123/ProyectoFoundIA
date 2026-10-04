package com.proyectointegrador.movil.model

/** GET /api/categorias */
data class Categoria(
    val id: Long = 0,
    val nombre: String? = null,
    val descripcion: String? = null,
    val estado: Boolean = true
)
