package com.proyectointegrador.movil.data.local.dao

import androidx.room.*
import com.proyectointegrador.movil.data.local.entity.PublicacionPendienteEntity

@Dao
interface PublicacionesPendientesDao {
    @Query("SELECT * FROM publicaciones_pendientes ORDER BY localId") suspend fun pendientes(): List<PublicacionPendienteEntity>
    @Insert suspend fun agregar(fila: PublicacionPendienteEntity): Long
    @Query("DELETE FROM publicaciones_pendientes WHERE localId = :localId") suspend fun quitar(localId: Long)
    @Query("DELETE FROM publicaciones_pendientes") suspend fun borrar()
}
