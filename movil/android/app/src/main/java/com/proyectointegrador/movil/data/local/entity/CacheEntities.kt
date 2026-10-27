package com.proyectointegrador.movil.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/** Únicamente DTO públicos ya consultados; nunca perfiles, JWT, claves o bandejas. */
@Entity(tableName = "publicaciones_cache")
data class PublicacionCache(@PrimaryKey val id: Long, val contenido: String, val actualizadoEn: Long)

/** Conserva pertenencia y orden de cada consulta exacta; no inventa resultados offline. */
@Entity(tableName = "consultas_cache")
data class ConsultaCache(@PrimaryKey val clave: String, val idsJson: String, val actualizadoEn: Long)

@Entity(tableName = "categorias_cache")
data class CategoriaCache(@PrimaryKey val id: Long, val nombre: String?, val descripcion: String?, val estado: Boolean, val orden: Int)
