package com.proyectointegrador.movil

import com.proyectointegrador.movil.data.remote.ApiService
import com.proyectointegrador.movil.data.repository.ApiAsistenteRepository
import com.proyectointegrador.movil.domain.ErrorDatos
import com.proyectointegrador.movil.domain.MAXIMO_VOZ
import com.proyectointegrador.movil.domain.mensajeErrorDictado
import com.proyectointegrador.movil.domain.textoParaVoz
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okio.Buffer
import org.junit.After
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class VozTest {
    private lateinit var servidor: MockWebServer
    private lateinit var repo: ApiAsistenteRepository

    @Before fun preparar() {
        servidor = MockWebServer()
        servidor.start()
        val api = Retrofit.Builder().baseUrl(servidor.url("/")).addConverterFactory(GsonConverterFactory.create()).build().create(ApiService::class.java)
        repo = ApiAsistenteRepository(api)
    }

    @After fun cerrar() { servidor.shutdown() }

    @Test fun cadaErrorDelReconocedorTieneUnMensajeQueOrientaALaPersona() {
        assertTrue(mensajeErrorDictado(2).contains("conexión"))
        assertTrue(mensajeErrorDictado(6).contains("No te escuché"))
        assertTrue(mensajeErrorDictado(7).contains("No entendí"))
        assertTrue(mensajeErrorDictado(9).contains("permiso"))
        assertTrue(mensajeErrorDictado(3).contains("micrófono"))
    }

    @Test fun unCodigoDesconocidoNuncaDejaElMensajeVacio() {
        assertTrue(mensajeErrorDictado(99).isNotBlank())
        assertTrue(mensajeErrorDictado(-1).isNotBlank())
    }

    @Test fun elTextoParaLaVozSeLimpiaYNoPasaDelLimiteDelServidor() {
        assertEquals("Hola FINDI", textoParaVoz("  Hola \n  FINDI  "))
        val largo = ("Esta es una frase de prueba. ").repeat(60)
        val recortado = textoParaVoz(largo)
        assertTrue(recortado.length <= MAXIMO_VOZ)
        assertTrue("Se corta al final de una frase", recortado.endsWith("."))
        assertTrue(textoParaVoz("x".repeat(2000)).length <= MAXIMO_VOZ)
    }

    @Test fun laVozNaturalPideElAudioAlServidorYLoDevuelve() = runBlocking {
        val mp3 = byteArrayOf(0x49, 0x44, 0x33, 1, 2, 3)
        servidor.enqueue(MockResponse().setResponseCode(200).setHeader("Content-Type", "audio/mpeg").setBody(Buffer().write(mp3)))
        val audio = repo.vozNatural("Encontré dos publicaciones.")
        assertArrayEquals(mp3, audio)
        val pedido = servidor.takeRequest()
        assertEquals("/api/asistente/voz", pedido.path)
        assertTrue(pedido.body.readUtf8().contains("\"texto\":\"Encontré dos publicaciones.\""))
    }

    @Test fun siElServidorNoTieneLaVozConfiguradaFallaConSuCodigoParaUsarLaVozDelTelefono() = runBlocking {
        servidor.enqueue(MockResponse().setResponseCode(503).setBody("{\"mensaje\":\"La voz natural de FINDI no está configurada\"}"))
        try {
            repo.vozNatural("Hola")
            fail("Debía fallar")
        } catch (e: ErrorDatos) {
            assertEquals(503, e.estadoHttp)
        }
    }

    @Test fun unTextoVacioNoLlegaAlServidor() = runBlocking {
        try {
            repo.vozNatural("   ")
            fail("Debía fallar")
        } catch (e: ErrorDatos) {
            assertEquals(0, servidor.requestCount)
        }
    }
}
