package com.proyectointegrador.movil

import com.google.gson.Gson
import com.proyectointegrador.movil.data.local.ColaPublicaciones
import com.proyectointegrador.movil.data.local.PublicacionPendiente
import com.proyectointegrador.movil.data.remote.ApiService
import com.proyectointegrador.movil.data.repository.ApiPublicacionesRepository
import com.proyectointegrador.movil.data.repository.CuentaRepository
import com.proyectointegrador.movil.domain.ErrorDatos
import com.proyectointegrador.movil.model.Objeto
import com.proyectointegrador.movil.model.PublicacionRequest
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class ColaPublicacionesMemoria : ColaPublicaciones {
    val filas = mutableListOf<PublicacionPendiente>()
    private var siguiente = 1L
    var limpiada = 0
    override suspend fun agregar(idCliente: String, datos: PublicacionRequest) = PublicacionPendiente(siguiente++, idCliente, datos, 0).also { filas += it }
    override suspend fun pendientes() = filas.toList()
    override suspend fun quitar(localId: Long) { filas.removeAll { it.localId == localId } }
    override suspend fun limpiar() { filas.clear(); limpiada++ }
}

class PublicacionesSinConexionTest {
    private lateinit var servidor: MockWebServer
    private lateinit var cuenta: CuentaRepository
    private lateinit var sesion: SesionPrueba
    private val cola = ColaPublicacionesMemoria()
    private val claves = java.util.concurrent.CopyOnWriteArrayList<String>()
    private val nombres = java.util.concurrent.CopyOnWriteArrayList<String>()
    @Volatile private var modo = "ok"
    @Volatile private var avisos = 0
    private var siguienteId = 40L

    private val datos = PublicacionRequest(3, "Mochila azul", "Con llavero", "2026-11-20", "PERDIDO", "Biblioteca")

    @Before fun preparar() {
        servidor = MockWebServer()
        servidor.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                if (request.method != "POST" || request.requestUrl!!.encodedPath != "/api/objetos") return MockResponse().setResponseCode(404)
                claves += request.getHeader("Idempotency-Key").orEmpty()
                val cuerpo = request.body.readUtf8()
                return when (modo) {
                    "sinRed" -> MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START)
                    "400" -> MockResponse().setResponseCode(400).setBody("{\"mensaje\":\"Fecha inválida\"}")
                    "401" -> MockResponse().setResponseCode(401).setBody("{\"mensaje\":\"Sesión vencida\"}")
                    "500" -> MockResponse().setResponseCode(500)
                    else -> {
                        nombres += Regex("\"nombre\":\"([^\"]*)\"").find(cuerpo)?.groupValues?.get(1).orEmpty()
                        MockResponse().setResponseCode(201).setBody(Gson().toJson(Objeto(id = siguienteId++, nombre = "creada")))
                    }
                }
            }
        }
        servidor.start()
        val api = Retrofit.Builder().baseUrl(servidor.url("/")).addConverterFactory(GsonConverterFactory.create()).build().create(ApiService::class.java)
        sesion = SesionPrueba().also { it.guardar("prueba") }
        cuenta = CuentaRepository(api, ApiPublicacionesRepository(api, CachePrueba()), sesion, cola = cola, alEncolarPublicacion = { avisos++ })
    }

    @After fun cerrar() { runBlocking { kotlinx.coroutines.delay(300) }; servidor.shutdown() }

    @Test fun conConexionPublicaYEnviaLaClave() = runBlocking {
        val id = cuenta.publicarOEncolar(datos, "clave-uno-1234", llevaFoto = false)
        assertEquals(40L, id)
        assertEquals(listOf("clave-uno-1234"), claves.toList())
        assertTrue(cola.filas.isEmpty())
        assertEquals(0, avisos)
    }

    @Test fun sinConexionYSinFotoQuedaEnLaColaConLaMismaClaveYSeAvisaAlTrabajo() = runBlocking {
        modo = "sinRed"
        val id = cuenta.publicarOEncolar(datos, "clave-dos-5678", llevaFoto = false)
        assertNull("Devuelve null: no se creó todavía", id)
        assertEquals("clave-dos-5678", cola.filas.single().idCliente)
        assertEquals("Mochila azul", cola.filas.single().datos.nombre)
        assertEquals(1, avisos)
        assertEquals("El primer intento ya llevaba la clave", listOf("clave-dos-5678"), claves.toList())
    }

    @Test fun sinConexionYConFotoNoSeEncolaYSeExplica() = runBlocking {
        modo = "sinRed"
        try {
            cuenta.publicarOEncolar(datos, "clave-tres-9012", llevaFoto = true)
            fail("Debía avisar que la foto necesita conexión")
        } catch (e: ErrorDatos) {
            assertTrue(e.mensaje.contains("foto"))
        }
        assertTrue(cola.filas.isEmpty())
    }

    @Test fun unRechazoDelServidorNoSeEncola() = runBlocking {
        modo = "400"
        try {
            cuenta.publicarOEncolar(datos, "clave-cuatro-3456", llevaFoto = false)
            fail("Debía fallar")
        } catch (e: ErrorDatos) {
            assertEquals(400, e.estadoHttp)
        }
        assertTrue(cola.filas.isEmpty())
    }

    @Test fun alVolverLaRedSeEnvianEnOrdenConLaMismaClaveYSeVaciaLaCola() = runBlocking {
        modo = "sinRed"
        cuenta.publicarOEncolar(datos.copy(nombre = "Primera"), "clave-a-00000001", false)
        cuenta.publicarOEncolar(datos.copy(nombre = "Segunda"), "clave-b-00000002", false)
        claves.clear()
        modo = "ok"
        val resultado = cuenta.enviarPublicacionesPendientes()
        assertEquals(2, resultado.enviadas); assertFalse(resultado.reintentar)
        assertEquals(listOf("clave-a-00000001", "clave-b-00000002"), claves.toList())
        assertEquals(listOf("Primera", "Segunda"), nombres.toList())
        assertTrue(cola.filas.isEmpty())
        // Una segunda vez no envía nada.
        claves.clear()
        assertEquals(0, cuenta.enviarPublicacionesPendientes().enviadas)
        assertTrue(claves.isEmpty())
    }

    @Test fun sigueSinRedSeConservaTodoYSePideReintentar() = runBlocking {
        modo = "sinRed"
        cuenta.publicarOEncolar(datos, "clave-x-00000003", false)
        val resultado = cuenta.enviarPublicacionesPendientes()
        assertTrue(resultado.reintentar); assertEquals(0, resultado.enviadas)
        assertEquals(1, cola.filas.size)
    }

    @Test fun unErrorDelServidorPidePrimeroReintentarYSeConserva() = runBlocking {
        modo = "sinRed"
        cuenta.publicarOEncolar(datos, "clave-y-00000004", false)
        modo = "500"
        val resultado = cuenta.enviarPublicacionesPendientes()
        assertTrue(resultado.reintentar)
        assertEquals(1, cola.filas.size)
    }

    @Test fun siElServidorLaRechazaSeDescartaYSeCuenta() = runBlocking {
        modo = "sinRed"
        cuenta.publicarOEncolar(datos, "clave-z-00000005", false)
        modo = "400"
        val resultado = cuenta.enviarPublicacionesPendientes()
        assertEquals(1, resultado.rechazadas); assertFalse(resultado.reintentar)
        assertTrue(cola.filas.isEmpty())
    }

    @Test fun conLaSesionVencidaSeConservaYNoSeReintenta() = runBlocking {
        modo = "sinRed"
        cuenta.publicarOEncolar(datos, "clave-w-00000006", false)
        modo = "401"
        val resultado = cuenta.enviarPublicacionesPendientes()
        assertFalse(resultado.reintentar)
        assertEquals("Se conserva para cuando la persona vuelva a entrar", 1, cola.filas.size)
    }

    @Test fun sinSesionNoSeEnviaNada() = runBlocking {
        modo = "sinRed"
        cuenta.publicarOEncolar(datos, "clave-v-00000007", false)
        sesion.cerrar()
        modo = "ok"
        claves.clear()
        assertEquals(0, cuenta.enviarPublicacionesPendientes().enviadas)
        assertTrue(claves.isEmpty())
    }

    @Test fun cerrarSesionBorraLaCola() = runBlocking {
        modo = "sinRed"
        cuenta.publicarOEncolar(datos, "clave-u-00000008", false)
        assertNotNull(cola.filas.firstOrNull())
        cuenta.cerrarSesion()
        assertTrue(cola.filas.isEmpty())
        assertEquals(1, cola.limpiada)
    }
}
