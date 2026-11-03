package com.proyectointegrador.movil

import com.google.gson.Gson
import com.proyectointegrador.movil.data.repository.*
import com.proyectointegrador.movil.domain.*
import com.proyectointegrador.movil.model.*
import com.proyectointegrador.movil.network.ApiService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import okhttp3.mockwebserver.*
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import org.junit.*
import org.junit.Assert.*
import java.util.concurrent.TimeUnit

class RepositoriosMvvmTest {
    private lateinit var servidor:MockWebServer
    private lateinit var api:ApiService
    private lateinit var cache:CachePrueba
    private lateinit var publicaciones:ApiPublicacionesRepository
    private lateinit var sesion:SesionPrueba
    private lateinit var cuenta:CuentaRepository
    @Before fun preparar(){servidor=MockWebServer();servidor.start()
        api=Retrofit.Builder().baseUrl(servidor.url("/")).addConverterFactory(GsonConverterFactory.create()).build().create(ApiService::class.java)
        cache=CachePrueba();publicaciones=ApiPublicacionesRepository(api,cache);sesion=SesionPrueba();sesion.guardar("no-se-persiste")
        cuenta=CuentaRepository(api,publicaciones,sesion)
    }
    @After fun cerrar(){servidor.shutdown()}
    private fun responder(datos:Any,codigo:Int=200){servidor.enqueue(MockResponse().setResponseCode(codigo).setHeader("Content-Type","application/json").setBody(Gson().toJson(datos)))}
    @Test fun errorCincoCientosUsaSoloConsultaExactaYLaIdentificaComoCopia()=runBlocking {
        responder(listOf(Objeto(id=1,nombre="Mochila")))
        assertFalse(publicaciones.buscar("mochila","PERDIDO").first().desdeCache)
        responder(mapOf("mensaje" to "Fallo temporal"),503)
        val local=publicaciones.buscar("mochila","PERDIDO").first()
        assertTrue(local.desdeCache);assertNotNull(local.aviso);assertEquals(1L,local.datos.single().id)
        responder(emptyMap<String,String>(),503)
        assertTrue(runCatching {publicaciones.buscar("mochila","ENCONTRADO").first()}.isFailure)
        assertFalse(cache.listas.keys.any {it.contains("mochila")})
    }
    @Test fun retiradoOProhibidoNuncaSeResuelveConCopiaAntigua()=runBlocking {
        for(codigo in listOf(401,403,404)) {
            cache.guardarDetalle(Objeto(id=9,nombre="Retirado"))
            responder(mapOf("mensaje" to "No disponible"),codigo)
            val error=runCatching {publicaciones.detalle(9).first()}.exceptionOrNull() as ErrorDatos
            assertEquals(codigo,error.estadoHttp);assertNull(cache.detalle(9))
        }
    }
    @Test fun recuperarSoloInvalidaCacheDespuesDeConfirmacionRemota()=runBlocking {
        cache.guardarDetalle(Objeto(id=1,nombre="Propio"))
        responder(emptyMap<String,String>(),409)
        assertTrue(runCatching {cuenta.recuperar("1-2")}.isFailure);assertEquals(0,cache.invalidaciones)
        responder(Recuperacion("1-2","ACEPTADA",1,"RECUPERADO","2026-10-27T12:00:00Z",false))
        assertEquals("RECUPERADO",cuenta.recuperar("1-2").estadoObjetoPerdido)
        assertEquals(1,cache.invalidaciones);assertNull(cache.detalle(1))
    }
    @Test fun publicarEditarYEliminarNoAnuncianExitoPorUnCambioLocal()=runBlocking {
        val datos=PublicacionRequest(1,"Mochila","Objeto de prueba","2026-10-20","PERDIDO","Biblioteca")
        responder(emptyMap<String,String>(),500)
        assertTrue(runCatching {cuenta.publicar(datos)}.isFailure);assertEquals(0,cache.invalidaciones)
        responder(Objeto(id=5,nombre="Mochila"),201);assertEquals(5L,cuenta.publicar(datos).id)
        responder(Objeto(id=5,nombre="Editado"));assertEquals("Editado",cuenta.editar(5,datos).nombre)
        servidor.enqueue(MockResponse().setResponseCode(204));cuenta.eliminar(5)
        assertEquals(3,cache.invalidaciones)
        repeat(4){val peticion=servidor.takeRequest();assertFalse(peticion.body.readUtf8().contains("token"))}
    }
    @Test fun cambioDeSesionDescartaRespuestaPrivadaTardia()=runBlocking {
        servidor.enqueue(MockResponse().setBody(Gson().toJson(Usuario(1,"Ana","QA","ana@qa.invalid","USUARIO","ACTIVO"))).setBodyDelay(250,TimeUnit.MILLISECONDS))
        val tarea=async {runCatching {cuenta.perfil()}.exceptionOrNull()}
        withContext(Dispatchers.IO){servidor.takeRequest()};sesion.cerrar();sesion.guardar("otra-sesion")
        assertTrue(tarea.await() is CancellationException)
    }
    @Test fun respuestaPublicaAnteriorAUnaMutacionNoReescribeLaCache()=runBlocking {
        servidor.enqueue(MockResponse().setBody(Gson().toJson(listOf(Objeto(id=4,nombre="Anterior")))).setBodyDelay(250,TimeUnit.MILLISECONDS))
        val tarea=async {runCatching {publicaciones.listar().first()}.exceptionOrNull()}
        withContext(Dispatchers.IO){servidor.takeRequest()};publicaciones.invalidar()
        assertTrue(tarea.await() is ErrorDatos);assertTrue(cache.listas.isEmpty())
    }
    @Test fun cancelarConsultaNoSeConvierteEnErrorDeRedNiFallback()=runBlocking {
        servidor.enqueue(MockResponse().setBody("[]").setBodyDelay(1,TimeUnit.SECONDS))
        val tarea=launch {publicaciones.listar().first()}
        withContext(Dispatchers.IO){servidor.takeRequest()};tarea.cancelAndJoin()
        assertTrue(tarea.isCancelled);assertTrue(cache.listas.isEmpty())
    }
}
