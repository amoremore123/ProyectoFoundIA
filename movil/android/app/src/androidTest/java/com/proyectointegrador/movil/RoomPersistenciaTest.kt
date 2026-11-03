package com.proyectointegrador.movil

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.proyectointegrador.movil.data.local.*
import com.proyectointegrador.movil.data.local.entity.*
import com.proyectointegrador.movil.model.*
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.flow.first
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class RoomPersistenciaTest {
    private val contexto=InstrumentationRegistry.getInstrumentation().targetContext
    private val nombre="room-persistencia-prueba.db"
    @get:Rule val migraciones=MigrationTestHelper(InstrumentationRegistry.getInstrumentation(),FoundIADatabase::class.java.canonicalName!!,FrameworkSQLiteOpenHelperFactory())
    private fun abrir()=Room.databaseBuilder(contexto,FoundIADatabase::class.java,nombre).addMigrations(FoundIADatabase.MIGRACION_1_2).build()
    @Before fun preparar(){contexto.deleteDatabase(nombre)}
    @After fun limpiar(){contexto.deleteDatabase(nombre);contexto.deleteDatabase("room-migracion-prueba.db")}

    @Test fun guardarCerrarYAbrirConservaDatosSinDuplicarlos()=runBlocking {
        var db=abrir();var cache=RoomCachePublica(db)
        cache.guardarLista("lista:",listOf(Objeto(id=31,nombre="Mochila azul")))
        cache.guardarLista("lista:",listOf(Objeto(id=31,nombre="Mochila actualizada")))
        cache.guardarCategorias(listOf(Categoria(7,"Mochilas")))
        db.close()
        db=abrir();cache=RoomCachePublica(db)
        assertEquals("Mochila actualizada",cache.lista("lista:")!!.datos.single().nombre)
        assertEquals(31L,cache.detalle(31)!!.datos.id)
        assertEquals("Mochilas",cache.observarCategorias().first().single().nombre)
        db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM publicaciones_cache").use {assertTrue(it.moveToFirst());assertEquals(1,it.getInt(0))}
        db.close()
    }
    @Test fun migracionUnoADosConservaLasTresTablasYActualizaCaducidad() {
        val antiguo=migraciones.createDatabase("room-migracion-prueba.db",1)
        antiguo.execSQL("INSERT INTO publicaciones_cache(id,contenido,actualizadoEn) VALUES (9,'{\"id\":9,\"nombre\":\"Dato previo\"}',1000)")
        antiguo.execSQL("INSERT INTO consultas_cache(clave,idsJson,actualizadoEn) VALUES ('lista:','[9]',1000)")
        antiguo.execSQL("INSERT INTO categorias_cache(id,nombre,descripcion,estado,orden) VALUES (2,'Categoría previa',NULL,1,0)")
        antiguo.close()
        val actual=migraciones.runMigrationsAndValidate("room-migracion-prueba.db",2,true,FoundIADatabase.MIGRACION_1_2)
        actual.query("SELECT contenido,venceEn FROM publicaciones_cache WHERE id=9").use {
            assertTrue(it.moveToFirst());assertTrue(it.getString(0).contains("Dato previo"));assertEquals(86401000L,it.getLong(1))
        }
        actual.query("SELECT idsJson FROM consultas_cache").use {assertTrue(it.moveToFirst());assertEquals("[9]",it.getString(0))}
        actual.query("SELECT nombre FROM categorias_cache").use {assertTrue(it.moveToFirst());assertEquals("Categoría previa",it.getString(0))}
        actual.close()
    }
    @Test fun cacheCaducaYNoMezclaResultadosDeConsultas()=runBlocking {
        val db=abrir();var ahora=100000L;val cache=RoomCachePublica(db){ahora}
        cache.guardarLista("buscar-A",listOf(Objeto(id=1,nombre="Primera")))
        cache.guardarLista("buscar-B",listOf(Objeto(id=2,nombre="Segunda")))
        assertEquals(1L,cache.lista("buscar-A")!!.datos.single().id)
        cache.retirar(1)
        assertNull(cache.lista("buscar-A"));assertNull(cache.detalle(1))
        cache.guardarDetalle(Objeto(id=3,nombre="Temporal"));ahora+=86400001
        assertNull(cache.detalle(3));db.close()
    }
    @Test fun cacheAcotadaNoGuardaPerfilesNiCredenciales()=runBlocking {
        val db=abrir();val dao=db.cacheDao()
        dao.guardarPublicaciones((1L..230L).map {PublicacionCache(it,"{\"id\":$it}",it,86400000+it)})
        dao.limitarPublicaciones()
        db.openHelper.readableDatabase.query("SELECT COUNT(*) FROM publicaciones_cache").use {assertTrue(it.moveToFirst());assertEquals(200,it.getInt(0))}
        val tablas=mutableListOf<String>()
        db.openHelper.readableDatabase.query("SELECT name FROM sqlite_master WHERE type='table'").use {while(it.moveToNext())tablas.add(it.getString(0))}
        assertFalse(tablas.any {it.contains("usuario") || it.contains("sesion") || it.contains("password") || it.contains("notificacion")})
        db.close()
    }
}
