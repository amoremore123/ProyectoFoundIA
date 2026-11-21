package com.proyectointegrador.movil

import com.proyectointegrador.movil.data.remote.ApiService
import com.proyectointegrador.movil.data.repository.ApiPublicacionesRepository
import com.proyectointegrador.movil.data.repository.CuentaRepository
import com.proyectointegrador.movil.data.repository.FotoPreparada
import com.proyectointegrador.movil.data.repository.FotografiasRepository
import com.proyectointegrador.movil.domain.ErrorDatos
import com.proyectointegrador.movil.ui.viewmodel.FotoPerfilViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

@OptIn(ExperimentalCoroutinesApi::class)
class FotoPerfilTest {
    private lateinit var servidor: MockWebServer
    private lateinit var cuenta: CuentaRepository
    private val llamadas = java.util.concurrent.CopyOnWriteArrayList<String>()
    private var conFoto = true
    private val bytes = byteArrayOf(1, 2, 3, 4)

    private var fotoValida = true
    private val fotos = object : FotografiasRepository {
        override suspend fun preparar(uri: String): FotoPreparada {
            if (!fotoValida) throw ErrorDatos("Usa una fotografía JPEG o PNG de hasta 40 megapíxeles.")
            return FotoPreparada(byteArrayOf(9, 9, 9), "image/jpeg", "foto.jpg")
        }
    }

    @Before fun preparar() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        servidor = MockWebServer()
        servidor.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val ruta = request.requestUrl!!.encodedPath
                llamadas += "${request.method} $ruta"
                return when {
                    request.method == "GET" && ruta == "/api/perfil/foto" ->
                        if (conFoto) MockResponse().setResponseCode(200).setHeader("Content-Type", "image/jpeg").setBody(Buffer().write(bytes))
                        else MockResponse().setResponseCode(404).setBody("{\"mensaje\":\"Sin foto\"}")
                    request.method == "POST" && ruta == "/api/perfil/foto" ->
                        MockResponse().setResponseCode(200).setBody("{\"fotoPerfilUrl\":\"/api/perfil/foto?v=abc\"}")
                    request.method == "DELETE" && ruta == "/api/perfil/foto" -> MockResponse().setResponseCode(204)
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        servidor.start()
        val api = Retrofit.Builder().baseUrl(servidor.url("/")).addConverterFactory(GsonConverterFactory.create()).build().create(ApiService::class.java)
        cuenta = CuentaRepository(api, ApiPublicacionesRepository(api, CachePrueba()), SesionPrueba().also { it.guardar("prueba") })
    }

    @After fun cerrar() { runBlocking { kotlinx.coroutines.delay(400) }; servidor.shutdown(); Dispatchers.resetMain() }

    @Test fun cargaLosBytesDeLaFotoPropia() = runBlocking {
        val vm = FotoPerfilViewModel(cuenta, fotos)
        val estado = withTimeout(5000) { vm.foto.first { !it.cargando } }
        assertArrayEquals(bytes, estado.bytes)
    }

    @Test fun sinFotoElEstadoQuedaVacioSinError() = runBlocking {
        conFoto = false
        val vm = FotoPerfilViewModel(cuenta, fotos)
        val estado = withTimeout(5000) { vm.foto.first { !it.cargando } }
        assertNull(estado.bytes)
        assertNull(estado.error)
    }

    @Test fun cambiarLaFotoLaSubeYLaMuestra() = runBlocking {
        val vm = FotoPerfilViewModel(cuenta, fotos)
        withTimeout(5000) { vm.foto.first { !it.cargando } }
        vm.cambiar("content://galeria/1")
        withTimeout(5000) { vm.accion.first { it.mensaje != null } }
        assertTrue(llamadas.contains("POST /api/perfil/foto"))
        assertArrayEquals(byteArrayOf(9, 9, 9), vm.foto.value.bytes)
    }

    @Test fun unaFotoInvalidaNoLlegaAlServidor() = runBlocking {
        fotoValida = false
        val vm = FotoPerfilViewModel(cuenta, fotos)
        withTimeout(5000) { vm.foto.first { !it.cargando } }
        vm.cambiar("content://galeria/1")
        withTimeout(5000) { vm.accion.first { it.error != null } }
        assertEquals(0, llamadas.count { it.startsWith("POST") })
        assertNotNull(vm.accion.value.error)
    }

    @Test fun quitarLaFotoLaBorraDelServidorYDeLaPantalla() = runBlocking {
        val vm = FotoPerfilViewModel(cuenta, fotos)
        withTimeout(5000) { vm.foto.first { !it.cargando } }
        vm.quitar()
        withTimeout(5000) { vm.accion.first { it.mensaje != null } }
        assertTrue(llamadas.contains("DELETE /api/perfil/foto"))
        assertNull(vm.foto.value.bytes)
    }
}
