package com.proyectointegrador.movil.data.local

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.proyectointegrador.movil.data.local.dao.CacheDao
import com.proyectointegrador.movil.data.local.entity.*

@Database(entities = [PublicacionCache::class, ConsultaCache::class, CategoriaCache::class], version = 2, exportSchema = true)
abstract class FoundIADatabase : RoomDatabase() {
    abstract fun cacheDao(): CacheDao
    companion object {
        val MIGRACION_1_2 = object : Migration(1,2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE publicaciones_cache ADD COLUMN venceEn INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE publicaciones_cache SET venceEn = actualizadoEn + 86400000")
            }
        }
        fun abrir(context: Context): FoundIADatabase = Room.databaseBuilder(
            context.applicationContext, FoundIADatabase::class.java, "foundia-local.db"
        ).addMigrations(MIGRACION_1_2).build()
    }
}
