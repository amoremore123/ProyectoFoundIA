package com.proyectointegrador.movil

import com.google.gson.Gson
import com.proyectointegrador.movil.data.remote.ApiService
import com.proyectointegrador.movil.data.repository.ApiPublicacionesRepository
import com.proyectointegrador.movil.data.repository.CuentaRepository
import com.proyectointegrador.movil.data.repository.FotoPreparada
import com.proyectointegrador.movil.data.repository.FotografiasRepository
import com.proyectointegrador.movil.domain.errorMensajeChat
import com.proyectointegrador.movil.domain.fusionarMensajes
import com.proyectointegrador.movil.domain.textoSinLeer
import com.proyectointegrador.movil.model.Conversacion
import com.proyectointegrador.movil.model.MensajeChat
import com.proyectointegrador.movil.model.MensajesRespuesta
import com.proyectointegrador.movil.model.Participante
import com.proyectointegrador.movil.model.Usuario
import com.proyectointegrador.movil.ui.viewmodel.ChatViewModel
import com.proyectointegrador.movil.ui.viewmodel.ConversacionesViewModel
import com.proyectointegrador.movil.ui.viewmodel.IniciarChatViewModel
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
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class ChatDominioTest {
    private fun m(id: Long) = MensajeChat(id, 1, "Ana", "m$id", null, null)

    @Test fun elMensajeVacioOLargoSeRechaza() {
        assertNotNull(errorMensajeChat("   "))
        assertNotNull(errorMensajeChat("a".repeat(1001)))
        assertNull(errorMensajeChat("a".repeat(1000)))
        assertNull(errorMensajeChat("Hola"))
    }

    @Test fun fusionarNoDuplicaYOrdena() {
        val r = fusionarMensajes(listOf(m(3), m(1)), listOf(m(2), m(3)))
        assertEquals(listOf(1L, 2L, 3L), r.map { it.id })
    }

    @Test fun losSinLeerSeAcotan() {
        assertNull(textoSinLeer(0))
        assertEquals("4", textoSinLeer(4))
        assertEquals("99+", textoSinLeer(250))
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ChatAndroidTest {
    private lateinit var servidor: MockWebServer
    private lateinit var cuenta: CuentaRepository
    private val llamadas = java.util.concurrent.CopyOnWriteArrayList<String>()
    private val cuerpos = java.util.concurrent.CopyOnWriteArrayList<String>()
    private var soloLectura = false
    private var bloqueada = false
    private var caido = false
    private var mensajes = listOf(
        MensajeChat(1, 7, "Beto", "¿De qué color es?", null, "2026-11-20T10:00:00"),
        MensajeChat(2, 5, "Ana", "Azul", null, "2026-11-20T10:01:00")
    )
    private val fotos = object : FotografiasRepository {
        override suspend fun preparar(uri: String) = FotoPreparada(byteArrayOf(1, 2, 3), "image/jpeg", "foto.jpg")
    }

    private fun conversacion(id: Long = 9) = Conversacion(id, "ENTRE_USUARIOS", 3, "Mochila", if (soloLectura) "SOLO_LECTURA" else "ABIERTA",
        soloLectura, bloqueada, Participante(7, "Beto"), mensajes.lastOrNull(), 1)

    @Before fun preparar() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        servidor = MockWebServer()
        servidor.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val ruta = request.requestUrl!!.encodedPath
                llamadas += "${request.method} $ruta"
                if (request.method == "POST") cuerpos += request.body.readUtf8()
                fun json(d: Any, codigo: Int = 200) = MockResponse().setResponseCode(codigo).setBody(Gson().toJson(d))
                if (caido && ruta.contains("/mensajes") && request.method == "GET") return MockResponse().setResponseCode(500)
                return when {
                    request.method == "GET" && ruta == "/api/perfil" -> json(Usuario(5, "Ana", "Prueba", "a@prueba.invalid", "USUARIO", "ACTIVO"))
                    request.method == "GET" && ruta == "/api/conversaciones" -> json(listOf(conversacion()))
                    request.method == "POST" && ruta == "/api/conversaciones" -> json(conversacion(9))
                    request.method == "GET" && ruta == "/api/conversaciones/9/mensajes" -> {
                        val despues = request.requestUrl!!.queryParameter("despuesDe")!!.toLong()
                        val nuevos = mensajes.filter { it.id > despues }
                        json(MensajesRespuesta(nuevos, nuevos.lastOrNull()?.id ?: despues, soloLectura, bloqueada))
                    }
                    request.method == "POST" && ruta == "/api/conversaciones/9/mensajes" -> json(MensajeChat(3, 5, "Ana", "Hola", null, "2026-11-20T10:02:00"), 201)
                    request.method == "POST" && ruta == "/api/conversaciones/9/leida" -> MockResponse().setResponseCode(204)
                    request.method == "POST" && ruta == "/api/conversaciones/9/bloqueo" -> { bloqueada = true; json(conversacion()) }
                    request.method == "DELETE" && ruta == "/api/conversaciones/9/bloqueo" -> { bloqueada = false; json(conversacion()) }
                    request.method == "POST" && ruta == "/api/conversaciones/9/mensajes/1/reporte" -> MockResponse().setResponseCode(204)
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        servidor.start()
        val api = Retrofit.Builder().baseUrl(servidor.url("/")).addConverterFactory(GsonConverterFactory.create()).build().create(ApiService::class.java)
        cuenta = CuentaRepository(api, ApiPublicacionesRepository(api, CachePrueba()), SesionPrueba().also { it.guardar("prueba") })
    }

    @After fun cerrar() { runBlocking { kotlinx.coroutines.delay(400) }; servidor.shutdown(); Dispatchers.resetMain() }

    private suspend fun chat(): ChatViewModel {
        val vm = ChatViewModel(cuenta, fotos, 9, intervaloMs = 3_600_000)
        withTimeout(5000) { vm.chat.first { !it.cargando && it.miId != null } }
        return vm
    }

    @Test fun cargaLosMensajesYMarcaLaConversacionComoLeida() = runBlocking {
        val vm = chat()
        assertEquals(listOf(1L, 2L), vm.chat.value.mensajes.map { it.id })
        assertEquals(5L, vm.chat.value.miId)
        withTimeout(5000) { while (!llamadas.contains("POST /api/conversaciones/9/leida")) kotlinx.coroutines.delay(10) }
        vm.detenerSondeo()
    }

    @Test fun enviarAgregaElMensajeUnaSolaVezYLimpiaElCampo() = runBlocking {
        val vm = chat()
        var enviado = false
        vm.enviar("  Hola  ") { enviado = true }
        withTimeout(5000) { vm.chat.first { !it.enviando && it.mensajes.size == 3 } }
        assertTrue(enviado)
        assertTrue(cuerpos.contains("{\"texto\":\"Hola\"}"))
        assertEquals(listOf(1L, 2L, 3L), vm.chat.value.mensajes.map { it.id })
        vm.detenerSondeo()
    }

    @Test fun unMensajeVacioNoLlegaAlServidor() = runBlocking {
        val vm = chat()
        vm.enviar("   ")
        assertEquals(0, llamadas.count { it == "POST /api/conversaciones/9/mensajes" })
        assertNotNull(vm.chat.value.aviso)
        vm.detenerSondeo()
    }

    @Test fun enSoloLecturaOBloqueadaNoSeEnvia() = runBlocking {
        soloLectura = true
        val vm = chat()
        assertTrue(vm.chat.value.soloLectura)
        vm.enviar("Hola")
        assertEquals(0, llamadas.count { it == "POST /api/conversaciones/9/mensajes" })
        vm.detenerSondeo()
    }

    @Test fun bloquearYDesbloquearActualizanElEstado() = runBlocking {
        val vm = chat()
        vm.bloquear(true)
        withTimeout(5000) { vm.chat.first { it.bloqueada } }
        vm.bloquear(false)
        withTimeout(5000) { vm.chat.first { !it.bloqueada } }
        vm.detenerSondeo()
    }

    @Test fun elReporteExigeUnMotivoYSeEnvia() = runBlocking {
        val vm = chat()
        vm.reportar(1, "ab")
        assertEquals(0, llamadas.count { it.endsWith("/reporte") })
        vm.reportar(1, "Lenguaje ofensivo")
        withTimeout(5000) { vm.accion.first { it.mensaje != null } }
        assertTrue(llamadas.contains("POST /api/conversaciones/9/mensajes/1/reporte"))
        vm.detenerSondeo()
    }

    @Test fun unFalloDeRedConservaLosMensajesYMuestraElError() = runBlocking {
        val vm = chat()
        caido = true
        vm.reintentar()
        withTimeout(5000) { vm.chat.first { it.error != null } }
        assertEquals(2, vm.chat.value.mensajes.size)
        vm.detenerSondeo()
    }

    @Test fun laListaDeConversacionesSeCarga() = runBlocking {
        val vm = ConversacionesViewModel(cuenta)
        val estado = withTimeout(5000) { vm.estado.first { !it.cargando } }
        assertEquals(9L, estado.datos!!.datos.single().id)
        assertEquals("Beto", estado.datos!!.datos.single().otro!!.nombre)
    }

    @Test fun iniciarPorContactoAvisaQuePantallaAbrir() = runBlocking {
        val vm = IniciarChatViewModel(cuenta)
        vm.porContacto(4)
        val evento = withTimeout(5000) { vm.evento.first { it != null } }
        assertEquals("conversacion/9", evento)
        val cuerpo = cuerpos.first { it.contains("contactoId") }
        assertFalse(cuerpo.contains("reclamoId"))
    }
}
