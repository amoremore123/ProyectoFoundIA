package com.proyectointegrador.movil.data.repository

/** Fotos privadas ya descargadas, solo en memoria: se borran al cerrar sesión y no pasan de 20. */
class ImagenesPrivadas(private val maximo: Int = 20) {
    private val filas = object : LinkedHashMap<String, ByteArray>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, ByteArray>?) = size > maximo
    }
    @Synchronized fun obtener(ruta: String): ByteArray? = filas[ruta]
    @Synchronized fun guardar(ruta: String, bytes: ByteArray) { filas[ruta] = bytes }
    @Synchronized fun limpiar() = filas.clear()
    @Synchronized fun tamano() = filas.size
}
