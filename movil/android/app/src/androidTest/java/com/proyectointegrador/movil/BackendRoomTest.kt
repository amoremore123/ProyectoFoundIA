package com.proyectointegrador.movil

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.gson.Gson
import com.proyectointegrador.movil.di.Inyeccion
import com.proyectointegrador.movil.model.*
import com.proyectointegrador.movil.ui.viewmodel.PublicarViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import org.junit.*
import org.junit.Assert.*
import org.junit.runner.RunWith
import java.io.File

/** Opt-in explícito y origen QA fijo: nunca escribe en la API habitual del equipo. */
@RunWith(AndroidJUnit4::class)
class BackendRoomTest {
    @Test fun publicarEditarEliminarYRecuperarContraApiReal()=runBlocking {
        Assume.assumeTrue(InstrumentationRegistry.getArguments().getString("probarApiReal")=="true")
        assertEquals("http://10.0.2.2:18083/",BuildConfig.API_URL)
        val instrumentation=InstrumentationRegistry.getInstrumentation()
        val contenedor=Inyeccion.contenedor;val cuenta=contenedor.cuenta;val publicaciones=contenedor.publicaciones
        cuenta.login("ana@foundia.dev","Admin123!")
        val categoria=publicaciones.categorias().datos.first().id
        lateinit var vm:PublicarViewModel
        instrumentation.runOnMainSync {vm=PublicarViewModel(cuenta,publicaciones)}
        withTimeout(20000){vm.estado.first {!it.cargando}}
        instrumentation.runOnMainSync {
            vm.editar("nombre","ROOM PUBLICADO QA");vm.editar("descripcion","Publicación creada por ViewModel Android con API real")
            vm.editar("fecha","2026-10-20");vm.editar("ubicacion","Biblioteca QA");vm.categoria(categoria);vm.publicar()
        }
        val creado=withTimeout(20000){vm.evento.filterNotNull().first()}.toLong()
        assertEquals("ROOM PUBLICADO QA",publicaciones.detalle(creado).first().datos.nombre)
        val body=PublicacionRequest(categoria,"ROOM EDITAR QA","Objeto sintético QA","2026-10-20","PERDIDO","Biblioteca QA")
        val editado=cuenta.publicar(body).id
        publicaciones.detalle(editado).first()
        cuenta.editar(editado,body.copy(nombre="ROOM EDITADO QA"))
        assertNull(contenedor.base.cacheDao().publicacion(editado))
        assertEquals("ROOM EDITADO QA",publicaciones.detalle(editado).first().datos.nombre)
        val eliminado=cuenta.publicar(body.copy(nombre="ROOM ELIMINADO QA")).id
        publicaciones.detalle(eliminado).first();cuenta.eliminar(eliminado)
        assertNull(contenedor.base.cacheDao().publicacion(eliminado))
        assertTrue(runCatching {publicaciones.detalle(eliminado).first()}.isFailure)
        val perdido=cuenta.publicar(body.copy(nombre="ROOM RECUPERADO QA")).id
        cuenta.login("luis@foundia.dev","Admin123!")
        val encontrado=cuenta.publicar(body.copy(nombre="ROOM RECUPERADO QA",tipo="ENCONTRADO")).id
        cuenta.login("ana@foundia.dev","Admin123!")
        val resultado=cuenta.recuperar("$perdido-$encontrado")
        assertEquals("RECUPERADO",resultado.estadoObjetoPerdido)
        assertEquals("RECUPERADO",publicaciones.detalle(perdido).first().datos.estado)
        assertEquals("ACTIVO",publicaciones.detalle(encontrado).first().datos.estado)
        // Alimentar la caché que se verificará tras cerrar el proceso y retirar la conexión.
        val lista=publicaciones.listar().first();assertFalse(lista.desdeCache)
        assertTrue(lista.datos.any {it.id==creado})
        val evidencia=mapOf("creado" to creado,"editado" to editado,"eliminado" to eliminado,"recuperado" to perdido,
            "hallazgoConservado" to encontrado,"fuenteServidor" to "API QA + MySQL :13370","publicadoDesdeViewModel" to true)
        File(instrumentation.targetContext.filesDir,"room-api-qa.json").writeText(Gson().toJson(evidencia))
    }
}
