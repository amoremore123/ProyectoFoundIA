package com.proyectointegrador.movil

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
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
    @get:Rule val migraciones=MigrationTestHelper(InstrumentationRegistry.getInstrumentation(),FoundIADatabase::class.java)
    private fun abrir()=Room.databaseBuilder(contexto,FoundIADatabase::class.java,nombre).addMigrations(FoundIADatabase.MIGRACION_1_2,FoundIADatabase.MIGRACION_2_3).build()
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

    @Test fun migracionDosATresConservaLaCacheYCreaLasTablasDelChat() {
        val v2=migraciones.createDatabase("room-migracion-prueba.db",2)
        v2.execSQL("INSERT INTO publicaciones_cache(id,contenido,actualizadoEn,venceEn) VALUES (5,'{\"id\":5,\"nombre\":\"Previo\"}',1000,2000)")
        v2.close()
        val v3=migraciones.runMigrationsAndValidate("room-migracion-prueba.db",3,true,FoundIADatabase.MIGRACION_2_3)
        v3.query("SELECT contenido FROM publicaciones_cache WHERE id=5").use {assertTrue(it.moveToFirst());assertTrue(it.getString(0).contains("Previo"))}
        v3.query("SELECT COUNT(*) FROM publicaciones_cache").use {assertTrue(it.moveToFirst());assertEquals(1,it.getInt(0))}
        for(tabla in listOf("conversaciones_cache","mensajes_chat_cache","mensajes_pendientes"))
            v3.query("SELECT COUNT(*) FROM "+tabla).use {assertTrue(it.moveToFirst());assertEquals(0,it.getInt(0))}
        v3.close()
    }
    @Test fun elChatGuardadoSobreviveAlCierreLaColaSeOrdenaYLimpiarBorraTodo()=runBlocking {
        var db=abrir();var chat=RoomAlmacenChat(db)
        chat.guardarConversaciones(listOf(Conversacion(9,"ENTRE_USUARIOS",3,"Mochila","ABIERTA",false,false,Participante(7,"Beto"),null,2)))
        chat.guardarMensajes(9,listOf(MensajeChat(1,7,"Beto","hola",null,null),MensajeChat(2,5,"Ana","azul",null,null)))
        chat.guardarMensajes(9,listOf(MensajeChat(2,5,"Ana","azul actualizado",null,null)))
        val a=chat.agregarPendiente(9,"primero");val b=chat.agregarPendiente(9,"segundo")
        db.close()
        db=abrir();chat=RoomAlmacenChat(db)
        assertEquals(9L,chat.conversaciones().single().id)
        assertEquals(listOf("hola","azul actualizado"),chat.mensajes(9).map {it.texto})
        assertEquals(listOf("primero","segundo"),chat.pendientes(9).map {it.texto})
        assertTrue(b.localId>a.localId)
        chat.quitarPendiente(a.localId)
        assertEquals(listOf("segundo"),chat.pendientes(9).map {it.texto})
        chat.limpiar()
        assertTrue(chat.conversaciones().isEmpty()&&chat.mensajes(9).isEmpty()&&chat.pendientes(9).isEmpty())
        db.close()
    }
    @Test fun elChatGuardaComoMaximo200MensajesPorConversacion()=runBlocking {
        val db=abrir();val chat=RoomAlmacenChat(db)
        chat.guardarMensajes(9,(1L..230L).map {MensajeChat(it,7,"Beto","m$it",null,null)})
        val guardados=chat.mensajes(9)
        assertEquals(200,guardados.size);assertEquals(31L,guardados.first().id);assertEquals(230L,guardados.last().id)
        db.close()
    }
}
