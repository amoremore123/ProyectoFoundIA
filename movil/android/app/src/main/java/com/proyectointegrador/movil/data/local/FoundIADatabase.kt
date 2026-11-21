package com.proyectointegrador.movil.data.local

import android.content.Context
import androidx.room.*
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.proyectointegrador.movil.data.local.dao.CacheDao
import com.proyectointegrador.movil.data.local.entity.*

@Database(entities = [PublicacionCache::class, ConsultaCache::class, CategoriaCache::class, ConversacionCache::class, MensajeChatCache::class, MensajePendienteEntity::class], version = 3, exportSchema = true)
abstract class FoundIADatabase : RoomDatabase() {
    abstract fun cacheDao(): CacheDao
    abstract fun chatDao(): com.proyectointegrador.movil.data.local.dao.ChatDao
    companion object {
        val MIGRACION_1_2 = object : Migration(1,2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE publicaciones_cache ADD COLUMN venceEn INTEGER NOT NULL DEFAULT 0")
                db.execSQL("UPDATE publicaciones_cache SET venceEn = actualizadoEn + 86400000")
            }
        }
        /** H55 y H65: tablas del chat sin conexión. Las anteriores no cambian. */
        val MIGRACION_2_3 = object : Migration(2,3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("CREATE TABLE IF NOT EXISTS `conversaciones_cache` (`id` INTEGER NOT NULL, `contenido` TEXT NOT NULL, `actualizadoEn` INTEGER NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE TABLE IF NOT EXISTS `mensajes_chat_cache` (`id` INTEGER NOT NULL, `conversacionId` INTEGER NOT NULL, `contenido` TEXT NOT NULL, PRIMARY KEY(`id`))")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_mensajes_chat_cache_conversacionId` ON `mensajes_chat_cache` (`conversacionId`)")
                db.execSQL("CREATE TABLE IF NOT EXISTS `mensajes_pendientes` (`localId` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `conversacionId` INTEGER NOT NULL, `texto` TEXT NOT NULL, `creadoEn` INTEGER NOT NULL)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_mensajes_pendientes_conversacionId` ON `mensajes_pendientes` (`conversacionId`)")
            }
        }
        fun abrir(context: Context): FoundIADatabase = Room.databaseBuilder(
            context.applicationContext, FoundIADatabase::class.java, "foundia-local.db"
        ).addMigrations(MIGRACION_1_2, MIGRACION_2_3).build()
    }
}
