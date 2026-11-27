package com.proyectointegrador.movil.model

/** H71: aviso destacado vigente (GET /api/avisos-destacados, público). */
data class AvisoDestacado(val id: Long, val titulo: String, val texto: String, val enlace: String?, val fechaFin: String?)
