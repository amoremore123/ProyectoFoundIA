package com.proyectointegrador.movil

import com.proyectointegrador.movil.data.remote.ApiService
import com.proyectointegrador.movil.data.repository.ApiPublicacionesRepository
import com.proyectointegrador.movil.data.repository.CuentaRepository
import com.proyectointegrador.movil.data.repository.ImagenesPrivadas
import com.proyectointegrador.movil.domain.ErrorDatos
import com.proyectointegrador.movil.domain.esRutaFotoPrivada
import com.proyectointegrador.movil.domain.rutaFotoDeConversacion
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class RutasPrivadasTest {
    @Test fun soloSeAceptanLasFotosPrivadasDelChatDeNuestraApi() {
        assertTrue(esRutaFotoPrivada("/api/conversaciones/7/mensajes/31/foto"))
        assertTrue(esRutaFotoPrivada("/api/conversaciones/7/foto"))
        assertEquals("/api/conversaciones/9/foto", rutaFotoDeConversacion(9))
    }

    @Test fun cualquierOtraRutaSeRechazaParaNoFiltrarElToken() {
        assertFalse(esRutaFotoPrivada(null))
        assertFalse(esRutaFotoPrivada(""))
        assertFalse(esRutaFotoPrivada("https://malo.example/api/conversaciones/7/foto"))
        assertFalse(esRutaFotoPrivada("//malo.example/api/conversaciones/7/foto"))
        assertFalse(esRutaFotoPrivada("/api/perfil"))
        assertFalse(esRutaFotoPrivada("/api/conversaciones/7/mensajes"))
        assertFalse(esRutaFotoPrivada("/api/conversaciones/abc/foto"))
        assertFalse(esRutaFotoPrivada("/api/conversaciones/7/foto/../../auth/logout"))
        assertFalse(esRutaFotoPrivada("/uploads/chat/archivo.png"))
    }

    @Test fun laMemoriaDeFotosNoPasaDelMaximoYSeLimpia() {
        val m = ImagenesPrivadas(maximo = 2)
        m.guardar("a", byteArrayOf(1)); m.guardar("b", byteArrayOf(2)); m.guardar("c", byteArrayOf(3))
        assertEquals(2, m.tamano())
        assertNull(m.obtener("a"))
        m.limpiar()
        assertEquals(0, m.tamano())
    }
}

class FotosPrivadasRepositorioTest {
    private lateinit var servidor: MockWebServer
    private lateinit var cuenta: CuentaRepository
    private val llamadas = java.util.concurrent.CopyOnWriteArrayList<String>()
    private val bytes = byteArrayOf(5, 6, 7, 8)

    @Before fun preparar() {
        servidor = MockWebServer()
        servidor.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                llamadas += request.method + " " + request.requestUrl!!.encodedPath
                return if (request.requestUrl!!.encodedPath == "/api/conversaciones/7/mensajes/31/foto")
                    MockResponse().setResponseCode(200).setHeader("Content-Type", "image/png").setBody(Buffer().write(bytes))
                else MockResponse().setResponseCode(404)
            }
        }
        servidor.start()
        val api = Retrofit.Builder().baseUrl(servidor.url("/")).addConverterFactory(GsonConverterFactory.create()).build().create(ApiService::class.java)
        cuenta = CuentaRepository(api, ApiPublicacionesRepository(api, CachePrueba()), SesionPrueba().also { it.guardar("prueba") })
    }

    @After fun cerrar() { servidor.shutdown() }

    @Test fun laFotoSePideUnaSolaVezYLuegoSale_DeLaMemoria() = runBlocking {
        val a = cuenta.fotoPrivada("/api/conversaciones/7/mensajes/31/foto")
        val b = cuenta.fotoPrivada("/api/conversaciones/7/mensajes/31/foto")
        assertArrayEquals(bytes, a); assertArrayEquals(bytes, b)
        assertEquals(1, llamadas.size)
    }

    @Test fun unaRutaAjenaNoLlegaNuncaAlServidor() = runBlocking {
        try {
            cuenta.fotoPrivada("https://malo.example/api/conversaciones/7/foto")
            fail("Debía rechazarse")
        } catch (e: ErrorDatos) {
            assertTrue(llamadas.isEmpty())
        }
    }

    @Test fun cerrarSesionBorraLasFotosDeLaMemoria() = runBlocking {
        cuenta.fotoPrivada("/api/conversaciones/7/mensajes/31/foto")
        cuenta.cerrarSesion()
        // Tras cerrar sesión ya no hay sesión: pedirla otra vez exige iniciar sesión (no sale de la memoria).
        try {
            cuenta.fotoPrivada("/api/conversaciones/7/mensajes/31/foto")
            fail("Sin sesión no debe devolver la foto")
        } catch (e: ErrorDatos) {
            assertEquals(401, e.estadoHttp)
        }
    }
}
