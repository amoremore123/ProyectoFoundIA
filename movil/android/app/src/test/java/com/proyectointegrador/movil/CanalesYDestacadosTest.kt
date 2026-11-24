package com.proyectointegrador.movil

import com.google.gson.Gson
import com.google.gson.JsonParser
import com.proyectointegrador.movil.data.remote.ApiService
import com.proyectointegrador.movil.data.repository.ApiPublicacionesRepository
import com.proyectointegrador.movil.data.repository.CuentaRepository
import com.proyectointegrador.movil.domain.canalDe
import com.proyectointegrador.movil.domain.canalValido
import com.proyectointegrador.movil.domain.etiquetaCanal
import com.proyectointegrador.movil.domain.rutaInternaDeEnlace
import com.proyectointegrador.movil.model.AvisoDestacado
import com.proyectointegrador.movil.ui.viewmodel.AvisosDestacadosViewModel
import com.proyectointegrador.movil.ui.viewmodel.PreferenciasViewModel
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

class CanalesDominioTest {
    @Test fun sinDatoOConUnCanalInvalidoSeUsaPush() {
        assertEquals("PUSH", canalDe(null, "mensajes"))
        assertEquals("PUSH", canalDe(mapOf("mensajes" to "SMS"), "mensajes"))
        assertEquals("CORREO", canalDe(mapOf("mensajes" to "CORREO"), "mensajes"))
        assertTrue(canalValido("AMBOS")); assertFalse(canalValido("sms")); assertFalse(canalValido(null))
        assertEquals("Solo en la bandeja", etiquetaCanal("NINGUNO"))
    }

    @Test fun losEnlacesDeAvisosDestacadosAbrenPantallasPropiasOnada() {
        assertEquals("buscar", rutaInternaDeEnlace("/explorar?origen=semana-2026"))
        assertEquals("detalle/12", rutaInternaDeEnlace("/objeto/12"))
        assertEquals("conversacion/4", rutaInternaDeEnlace("/conversaciones/4"))
        assertNull(rutaInternaDeEnlace(null))
        assertNull(rutaInternaDeEnlace("https://malo.example/objeto/1"))
        assertNull(rutaInternaDeEnlace("/admin/usuarios"))
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class CanalesYDestacadosTest {
    private lateinit var servidor: MockWebServer
    private lateinit var cuenta: CuentaRepository
    private val llamadas = java.util.concurrent.CopyOnWriteArrayList<String>()
    private val cuerpos = java.util.concurrent.CopyOnWriteArrayList<String>()
    @Volatile private var destacadosCaido = false

    private val preferencias = mapOf(
        "coincidencias" to true, "contactos" to true, "reclamos" to true, "recordatorios" to true,
        "canales" to mapOf("coincidencias" to "PUSH", "contactos" to "AMBOS", "reclamos" to "PUSH", "recordatorios" to "NINGUNO", "mensajes" to "PUSH", "sistema" to "PUSH")
    )

    @Before fun preparar() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        servidor = MockWebServer()
        servidor.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val ruta = request.requestUrl!!.encodedPath
                llamadas += request.method + " " + ruta
                if (request.method == "PUT") cuerpos += request.body.readUtf8()
                fun json(d: Any) = MockResponse().setResponseCode(200).setBody(Gson().toJson(d))
                return when {
                    ruta == "/api/notificaciones/preferencias" && request.method == "GET" -> json(preferencias)
                    ruta == "/api/notificaciones/preferencias" && request.method == "PUT" -> json(preferencias)
                    ruta == "/api/avisos-destacados" ->
                        if (destacadosCaido) MockResponse().setResponseCode(500)
                        else json(listOf(AvisoDestacado(3, "Semana de objetos perdidos", "Revisa la mesa de objetos.", "/explorar?origen=semana", "2026-11-30")))
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        servidor.start()
        val api = Retrofit.Builder().baseUrl(servidor.url("/")).addConverterFactory(GsonConverterFactory.create()).build().create(ApiService::class.java)
        cuenta = CuentaRepository(api, ApiPublicacionesRepository(api, CachePrueba()), SesionPrueba().also { it.guardar("prueba") })
    }

    @After fun cerrar() { runBlocking { kotlinx.coroutines.delay(400) }; servidor.shutdown(); Dispatchers.resetMain() }

    @Test fun elCanalDeCadaTipoSeCambiaYSeEnviaConTodoLoDemas() = runBlocking {
        val vm = PreferenciasViewModel(cuenta)
        val actuales = withTimeout(5000) { vm.estado.first { !it.cargando } }.datos!!
        assertEquals("AMBOS", actuales.canales!!["contactos"])
        vm.editarCanal("mensajes", "CORREO")
        assertEquals("CORREO", vm.edicion.value!!.canales!!["mensajes"])
        assertEquals("Los demás canales se conservan", "AMBOS", vm.edicion.value!!.canales!!["contactos"])
        vm.guardar()
        withTimeout(5000) { vm.accion.first { it.mensaje != null } }
        val enviado = JsonParser.parseString(cuerpos.single()).asJsonObject
        assertEquals("CORREO", enviado["canales"].asJsonObject["mensajes"].asString)
        assertEquals("NINGUNO", enviado["canales"].asJsonObject["recordatorios"].asString)
        assertTrue(enviado["coincidencias"].asBoolean)
    }

    @Test fun unTipoOUnCanalDesconocidoSeIgnora() = runBlocking {
        val vm = PreferenciasViewModel(cuenta)
        withTimeout(5000) { vm.estado.first { !it.cargando } }
        vm.editarCanal("chat", "PUSH")
        vm.editarCanal("mensajes", "SMS")
        assertNull("No se creó ninguna edición", vm.edicion.value)
    }

    @Test fun losAvisosDestacadosSeCargan() = runBlocking {
        val vm = AvisosDestacadosViewModel(cuenta)
        val estado = withTimeout(5000) { vm.estado.first { !it.cargando } }
        assertEquals("Semana de objetos perdidos", estado.datos!!.single().titulo)
        assertTrue(llamadas.contains("GET /api/avisos-destacados"))
    }

    @Test fun siFallanLosAvisosDestacadosNoSeMuestraNadaNiSeRompeInicio() = runBlocking {
        destacadosCaido = true
        val vm = AvisosDestacadosViewModel(cuenta)
        val estado = withTimeout(5000) { vm.estado.first { !it.cargando } }
        assertNotNull(estado.error)
        assertTrue(estado.datos.orEmpty().isEmpty())
    }
}
