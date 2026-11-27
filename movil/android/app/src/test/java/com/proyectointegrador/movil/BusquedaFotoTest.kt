package com.proyectointegrador.movil

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.proyectointegrador.movil.data.remote.ApiService
import com.proyectointegrador.movil.data.repository.*
import com.proyectointegrador.movil.domain.*
import com.proyectointegrador.movil.model.*
import com.proyectointegrador.movil.ui.viewmodel.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class FotoBusquedaDominioTest {
    @Test fun laFotoSeReduceSinAgrandarlaYConservaLaProporcion() {
        assertEquals(1024 to 768, ladoReducido(4000, 3000))
        assertEquals(768 to 1024, ladoReducido(3000, 4000))
        assertEquals("Una foto chica no se agranda", 640 to 480, ladoReducido(640, 480))
        assertEquals(1024 to 1, ladoReducido(4000, 1))
    }

    @Test fun seBuscaPorLasPalabrasQueReconocioLaIa() {
        val r = FotoBusquedaRespuesta("Lentes de sol con marco de carey", ObjetoCategoria(6, "Accesorio"), listOf("lentes", "sol", "marco", "carey"), listOf("marrón"))
        assertEquals("lentes sol marco", textoDeBusquedaPorFoto(r))
        assertEquals("Sin palabras se usa la descripción", "Lentes de sol", textoDeBusquedaPorFoto(FotoBusquedaRespuesta("Lentes de sol", null, emptyList(), null)))
        assertNull(textoDeBusquedaPorFoto(FotoBusquedaRespuesta("", null, emptyList(), emptyList())))
        assertNull(textoDeBusquedaPorFoto(FotoBusquedaRespuesta(null, null, listOf(" ", ""), null)))
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class BusquedaFotoTest {
    private lateinit var servidor: MockWebServer
    private lateinit var asistente: ApiAsistenteRepository
    private var codigo = 200
    private var cuerpo: Any = FotoBusquedaRespuesta()
    private val enviados = java.util.concurrent.CopyOnWriteArrayList<String>()
    private val fotos = object : FotografiasRepository {
        override suspend fun preparar(uri: String) = FotoPreparada(byteArrayOf(1), "image/jpeg", "f.jpg")
        override suspend fun prepararParaBusqueda(uri: String) = "data:image/jpeg;base64,AAAA"
    }

    @Before fun preparar() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        servidor = MockWebServer()
        servidor.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.requestUrl!!.encodedPath == "/api/asistente/buscar-por-foto") {
                    enviados += request.body.readUtf8()
                    return MockResponse().setResponseCode(codigo).setBody(Gson().toJson(cuerpo))
                }
                return MockResponse().setResponseCode(404)
            }
        }
        servidor.start()
        val api = Retrofit.Builder().baseUrl(servidor.url("/")).addConverterFactory(GsonConverterFactory.create()).build().create(ApiService::class.java)
        asistente = ApiAsistenteRepository(api)
    }

    @After fun cerrar() { servidor.shutdown(); Dispatchers.resetMain() }

    @Test fun unaFotoReconocidaDevuelveLoQueSeBusca() = runBlocking {
        cuerpo = FotoBusquedaRespuesta("Lentes de sol", ObjetoCategoria(6, "Accesorio"), listOf("lentes", "sol"), listOf("negro"))
        val vm = BusquedaFotoViewModel(asistente, fotos)
        vm.buscar("content://galeria/1")
        withTimeout(5000) { vm.resultado.first { it != null } }
        withTimeout(5000) { vm.accion.first { !it.ocupada } }
        assertEquals("lentes sol", vm.resultado.value!!.texto); assertEquals(6L, vm.resultado.value!!.categoriaId)
        assertEquals("Buscamos «lentes sol» según tu foto.", vm.accion.value.mensaje)
        assertEquals("data:image/jpeg;base64,AAAA", JsonParser.parseString(enviados.single()).asJsonObject["imagen"].asString)
    }

    @Test fun unaFotoSinObjetoReconocibleAvisaYNoBusca() = runBlocking {
        cuerpo = FotoBusquedaRespuesta("", null, emptyList(), emptyList())
        val vm = BusquedaFotoViewModel(asistente, fotos)
        vm.buscar("content://galeria/1")
        withTimeout(5000) { vm.accion.first { it.mensaje != null } }
        assertNull(vm.resultado.value)
        assertEquals("No se reconoció un objeto en la foto. Prueba con otra foto o escribe lo que buscas.", vm.accion.value.mensaje)
    }

    @Test fun conLaIaCaidaSeOfreceDescribirConPalabras() = runBlocking {
        codigo = 503; cuerpo = mapOf("mensaje" to "IA no disponible")
        val vm = BusquedaFotoViewModel(asistente, fotos)
        vm.buscar("content://galeria/1")
        withTimeout(5000) { vm.accion.first { it.error != null } }
        assertEquals("La búsqueda por foto no está disponible ahora. Describe el objeto con palabras.", vm.accion.value.error)
        assertNull(vm.resultado.value)
    }

    @Test fun conMuchasBusquedasSePideEsperar() = runBlocking {
        codigo = 429; cuerpo = mapOf("mensaje" to "Límite")
        val vm = BusquedaFotoViewModel(asistente, fotos)
        vm.buscar("content://galeria/1")
        withTimeout(5000) { vm.accion.first { it.error != null } }
        assertTrue(vm.accion.value.error!!.startsWith("Hiciste muchas búsquedas por foto"))
    }
}
