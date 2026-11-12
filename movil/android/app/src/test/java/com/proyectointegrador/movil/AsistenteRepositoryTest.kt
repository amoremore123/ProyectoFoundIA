package com.proyectointegrador.movil

import com.proyectointegrador.movil.data.remote.ApiService
import com.proyectointegrador.movil.data.repository.ApiAsistenteRepository
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.*
import org.junit.*
import org.junit.Assert.*
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class AsistenteRepositoryTest {
    private val servidor = MockWebServer()
    private lateinit var repo: ApiAsistenteRepository
    @Before fun preparar() {
        servidor.start()
        repo = ApiAsistenteRepository(Retrofit.Builder().baseUrl(servidor.url("/"))
            .addConverterFactory(GsonConverterFactory.create()).build().create(ApiService::class.java))
    }
    @After fun cerrar() { servidor.shutdown() }
    private fun responder(json: String, codigo: Int = 200) {
        servidor.enqueue(MockResponse().setResponseCode(codigo).setHeader("Content-Type", "application/json").setBody(json))
    }
    @Test fun interpretaYBuscaConElContratoCompartidoSinInventarObjetos() = runBlocking {
        responder("""{"intencion":"buscar","tipoBuscado":"ENCONTRADO","categoria":{"id":3},"palabras":["mochil"],"ubicacion":"taller","fuente":"local"}""")
        responder("""[{"id":8,"nombre":"Mochila negra","estado":"ACTIVO"},{"id":9,"nombre":"Oculta","estado":"OCULTO"}]""")
        val r = repo.responder("Busco una mochila en el taller")
        val interpretacion = servidor.takeRequest()
        assertEquals("/api/asistente/interpretar", interpretacion.path)
        assertTrue(interpretacion.body.readUtf8().contains("Busco una mochila en el taller"))
        val busqueda = servidor.takeRequest().requestUrl!!
        assertEquals("mochil", busqueda.queryParameter("q"))
        assertEquals("ENCONTRADO", busqueda.queryParameter("tipo"))
        assertEquals("3", busqueda.queryParameter("categoriaId"))
        assertEquals("taller", busqueda.queryParameter("ubicacion"))
        assertEquals(listOf(8L), r.objetos.map { it.id })
        assertEquals("local", r.fuente)
    }
    @Test fun laListaVaciaPermiteReportarSinExitoFicticio() = runBlocking {
        responder("""{"intencion":"buscar","palabras":["llaves"]}""")
        responder("[]")
        val r = repo.responder("perdí mis llaves")
        assertTrue(r.objetos.isEmpty())
        assertTrue(r.texto.contains("No encontré"))
        assertEquals("publicar?tipo=PERDIDO", r.accion?.ruta)
    }
    @Test fun misReportesNoSePierdeSiElMotorCompartidoNoReconoceLaIntencion() = runBlocking {
        responder("""{"intencion":"vacio","palabras":[]}""")
        val r = repo.responder("¿Cómo van mis reportes?")
        assertEquals("propios", r.accion?.ruta)
        assertEquals(1, servidor.requestCount)
    }
}
