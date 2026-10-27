package com.proyectointegrador.movil.data.local

import android.content.Context
import androidx.room.*
import com.proyectointegrador.movil.data.local.dao.CacheDao
import com.proyectointegrador.movil.data.local.entity.*

@Database(entities = [PublicacionCache::class, ConsultaCache::class, CategoriaCache::class], version = 1, exportSchema = true)
abstract class FoundIADatabase : RoomDatabase() {
    abstract fun cacheDao(): CacheDao
    companion object {
        fun abrir(context: Context): FoundIADatabase = Room.databaseBuilder(
            context.applicationContext, FoundIADatabase::class.java, "foundia-local.db"
        ).build()
    }
}
