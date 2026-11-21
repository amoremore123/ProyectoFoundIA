package com.proyectointegrador.movil

import com.google.gson.Gson
import com.proyectointegrador.movil.data.push.AlmacenDispositivo
import com.proyectointegrador.movil.data.push.ServicioFirebase
import com.proyectointegrador.movil.data.push.SesionExterna
import com.proyectointegrador.movil.data.push.SinFirebase
import com.proyectointegrador.movil.data.remote.ApiService
import com.proyectointegrador.movil.domain.CANAL_AVISOS
import com.proyectointegrador.movil.domain.CANAL_MENSAJES
import com.proyectointegrador.movil.domain.DestinoPush
import com.proyectointegrador.movil.domain.canalDeTipo
import com.proyectointegrador.movil.domain.destinoDeEnlace
import kotlinx.coroutines.runBlocking
import okhttp3.mockwebserver.Dispatcher
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import okhttp3.mockwebserver.RecordedRequest
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class PushFirebaseTest {
    private lateinit var servidor: MockWebServer
    private lateinit var api: ApiService
    private val peticiones = mutableListOf<String>()
    private var estadoDispositivo = 201
    private var estadoToken = 200

    private class AlmacenMemoria : AlmacenDispositivo {
        override var dispositivoId: Long? = null
        override var tokenRegistrado: String? = null
    }

    private class FirebasePrueba(
        override val disponible: Boolean = true,
        var token: String? = "token-de-prueba-1234567890"
    ) : ServicioFirebase {
        var entradas = mutableListOf<Pair<String, String>>()
        var salio = false
        var borroToken = false
        override suspend fun tokenPush() = token
        override fun borrarTokenPush() { borroToken = true }
        override suspend fun entrarConToken(tokenPersonalizado: String, uidEsperado: String): Boolean {
            entradas += tokenPersonalizado to uidEsperado
            return true
        }
        override fun salir() { salio = true }
    }

    @Before
    fun preparar() {
        servidor = MockWebServer()
        servidor.dispatcher = object : Dispatcher() {
            override fun dispatch(r: RecordedRequest): MockResponse {
                peticiones += r.method + " " + r.path
                val json = { cuerpo: String, codigo: Int -> MockResponse().setResponseCode(codigo).setHeader("Content-Type", "application/json").setBody(cuerpo) }
                return when {
                    r.method == "POST" && r.path == "/api/dispositivos" ->
                        json("""{"id":12,"plataforma":"ANDROID","fechaRegistro":"2026-11-20T10:00:00","fechaUltimoUso":"2026-11-20T10:00:00"}""", estadoDispositivo)
                    r.method == "DELETE" && r.path == "/api/dispositivos/12" -> MockResponse().setResponseCode(204)
                    r.method == "POST" && r.path == "/api/auth/firebase-token" ->
                        if (estadoToken == 200) json("""{"token":"personalizado","uid":"foundia-42","expiraEn":"2026-11-20T11:00:00Z"}""", 200)
                        else json("""{"mensaje":"Firebase no está configurado"}""", estadoToken)
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        servidor.start()
        api = Retrofit.Builder().baseUrl(servidor.url("/")).addConverterFactory(GsonConverterFactory.create(Gson())).build()
            .create(ApiService::class.java)
    }

    @After
    fun cerrar() = servidor.shutdown()

    @Test
    fun alEntrarRegistraElTelefonoYEntraAFirebaseConElUidDeLaCuenta() = runBlocking {
        val firebase = FirebasePrueba()
        val almacen = AlmacenMemoria()
        SesionExterna(api, firebase, almacen).alEntrar()
        assertEquals(12L, almacen.dispositivoId)
        assertEquals("token-de-prueba-1234567890", almacen.tokenRegistrado)
        assertEquals(listOf("personalizado" to "foundia-42"), firebase.entradas)
        assertTrue(peticiones.contains("POST /api/auth/firebase-token"))
    }

    @Test
    fun elMismoTokenNoSeRegistraDosVeces() = runBlocking {
        val almacen = AlmacenMemoria()
        val externa = SesionExterna(api, FirebasePrueba(), almacen)
        externa.registrarDispositivo()
        externa.registrarDispositivo()
        assertEquals(1, peticiones.count { it == "POST /api/dispositivos" })
    }

    @Test
    fun siFirebaseNoEstaConfiguradoNoSeLlamaAlServidor() = runBlocking {
        SesionExterna(api, SinFirebase, AlmacenMemoria()).alEntrar()
        assertTrue(peticiones.isEmpty())
    }

    @Test
    fun siElServidorResponde503AlPedirElTokenLaSesionContinua() = runBlocking {
        estadoToken = 503
        val firebase = FirebasePrueba()
        SesionExterna(api, firebase, AlmacenMemoria()).alEntrar()
        assertTrue(firebase.entradas.isEmpty())
    }

    @Test
    fun siRegistrarElDispositivoFallaNoSeLanzaError() = runBlocking {
        estadoDispositivo = 400
        val almacen = AlmacenMemoria()
        SesionExterna(api, FirebasePrueba(), almacen).registrarDispositivo()
        assertNull(almacen.dispositivoId)
    }

    @Test
    fun alSalirBorraElDispositivoElTokenLocalYLaSesionDeFirebase() = runBlocking {
        val firebase = FirebasePrueba()
        val almacen = AlmacenMemoria().apply { dispositivoId = 12; tokenRegistrado = "token-de-prueba-1234567890" }
        SesionExterna(api, firebase, almacen).alSalir()
        assertTrue(peticiones.contains("DELETE /api/dispositivos/12"))
        assertNull(almacen.dispositivoId)
        assertNull(almacen.tokenRegistrado)
        assertTrue(firebase.salio)
        assertTrue(firebase.borroToken)
    }

    @Test
    fun alSalirSinRedIgualLimpiaLoLocal() = runBlocking {
        servidor.shutdown()
        val firebase = FirebasePrueba()
        val almacen = AlmacenMemoria().apply { dispositivoId = 12; tokenRegistrado = "x" }
        SesionExterna(api, firebase, almacen).alSalir()
        assertNull(almacen.dispositivoId)
        assertTrue(firebase.salio)
    }

    @Test
    fun elEnlaceDelAvisoAbreLaPantallaCorrecta() {
        assertEquals(DestinoPush.Detalle(3), destinoDeEnlace("/objeto/3"))
        assertEquals(DestinoPush.Conversacion(7), destinoDeEnlace("/conversaciones/7"))
        assertEquals(DestinoPush.Detalle(9), destinoDeEnlace("/objeto/9?x=1#a"))
    }

    @Test
    fun unEnlaceDesconocidoOMalformadoAbreLosAvisos() {
        assertEquals(DestinoPush.Avisos, destinoDeEnlace(null))
        assertEquals(DestinoPush.Avisos, destinoDeEnlace(""))
        assertEquals(DestinoPush.Avisos, destinoDeEnlace("/objeto/abc"))
        assertEquals(DestinoPush.Avisos, destinoDeEnlace("/objeto/-4"))
        assertEquals(DestinoPush.Avisos, destinoDeEnlace("https://malo.example/objeto/3"))
        assertEquals(DestinoPush.Avisos, destinoDeEnlace("/admin/usuarios/1"))
    }

    @Test
    fun losMensajesDeChatUsanSuPropioCanal() {
        assertEquals(CANAL_MENSAJES, canalDeTipo("MENSAJE_CHAT"))
        assertEquals(CANAL_AVISOS, canalDeTipo("RECLAMO"))
        assertEquals(CANAL_AVISOS, canalDeTipo(null))
        assertFalse(SinFirebase.disponible)
    }
}
