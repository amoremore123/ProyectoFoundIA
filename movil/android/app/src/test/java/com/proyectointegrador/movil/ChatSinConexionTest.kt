package com.proyectointegrador.movil

import com.google.gson.Gson
import com.proyectointegrador.movil.data.local.AlmacenChat
import com.proyectointegrador.movil.data.local.PendienteChat
import com.proyectointegrador.movil.data.remote.ApiService
import com.proyectointegrador.movil.data.repository.ApiPublicacionesRepository
import com.proyectointegrador.movil.data.repository.CuentaRepository
import com.proyectointegrador.movil.data.repository.FotoPreparada
import com.proyectointegrador.movil.data.repository.FotografiasRepository
import com.proyectointegrador.movil.model.Conversacion
import com.proyectointegrador.movil.model.MensajeChat
import com.proyectointegrador.movil.model.MensajesRespuesta
import com.proyectointegrador.movil.model.Participante
import com.proyectointegrador.movil.model.Usuario
import com.proyectointegrador.movil.ui.viewmodel.ChatViewModel
import com.proyectointegrador.movil.ui.viewmodel.ConversacionesViewModel
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
import okhttp3.mockwebserver.SocketPolicy
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

/** Almacén en memoria con el mismo contrato que Room (la migración y las consultas reales se prueban en el emulador). */
class AlmacenChatMemoria : AlmacenChat {
    var lista = listOf<Conversacion>()
    val mensajesPorConversacion = mutableMapOf<Long, List<MensajeChat>>()
    val cola = mutableListOf<PendienteChat>()
    private var siguiente = 1L
    var limpiado = 0
    override suspend fun conversaciones() = lista
    override suspend fun guardarConversaciones(lista: List<Conversacion>) { this.lista = lista }
    override suspend fun mensajes(conversacionId: Long) = mensajesPorConversacion[conversacionId].orEmpty()
    override suspend fun guardarMensajes(conversacionId: Long, lista: List<MensajeChat>) { mensajesPorConversacion[conversacionId] = lista }
    override suspend fun pendientes(conversacionId: Long) = cola.filter { it.conversacionId == conversacionId }
    override suspend fun agregarPendiente(conversacionId: Long, texto: String) = PendienteChat(siguiente++, conversacionId, texto).also { cola += it }
    override suspend fun quitarPendiente(localId: Long) { cola.removeAll { it.localId == localId } }
    override suspend fun limpiar() { lista = emptyList(); mensajesPorConversacion.clear(); cola.clear(); limpiado++ }
}

@OptIn(ExperimentalCoroutinesApi::class)
class ChatSinConexionTest {
    private lateinit var servidor: MockWebServer
    private lateinit var cuenta: CuentaRepository
    private val almacen = AlmacenChatMemoria()
    private val llamadas = java.util.concurrent.CopyOnWriteArrayList<String>()
    private val cuerpos = java.util.concurrent.CopyOnWriteArrayList<String>()
    @Volatile private var sinRed = false
    @Volatile private var rechazar = 0
    private var siguienteId = 10L
    private val fotos = object : FotografiasRepository {
        override suspend fun preparar(uri: String) = FotoPreparada(byteArrayOf(1), "image/jpeg", "f.jpg")
    }

    private fun conversacion() = Conversacion(9, "ENTRE_USUARIOS", 3, "Mochila", "ABIERTA", false, false, Participante(7, "Beto"), null, 0)

    @Before fun preparar() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        servidor = MockWebServer()
        servidor.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val ruta = request.requestUrl!!.encodedPath
                llamadas += "${request.method} $ruta"
                if (sinRed) return MockResponse().setSocketPolicy(SocketPolicy.DISCONNECT_AT_START)
                if (request.method == "POST") cuerpos += request.body.readUtf8()
                fun json(d: Any, codigo: Int = 200) = MockResponse().setResponseCode(codigo).setBody(Gson().toJson(d))
                return when {
                    request.method == "GET" && ruta == "/api/perfil" -> json(Usuario(5, "Ana", "Prueba", "a@prueba.invalid", "USUARIO", "ACTIVO"))
                    request.method == "GET" && ruta == "/api/conversaciones" -> json(listOf(conversacion()))
                    request.method == "GET" && ruta == "/api/conversaciones/9/mensajes" -> json(MensajesRespuesta(emptyList(), request.requestUrl!!.queryParameter("despuesDe")!!.toLong(), false, false))
                    request.method == "POST" && ruta == "/api/conversaciones/9/mensajes" ->
                        if (rechazar > 0) { rechazar--; json(mapOf("mensaje" to "La conversación está en solo lectura"), 409) }
                        else json(MensajeChat(siguienteId++, 5, "Ana", "enviado", null, null), 201)
                    request.method == "POST" && ruta == "/api/conversaciones/9/leida" -> MockResponse().setResponseCode(204)
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        servidor.start()
        val api = Retrofit.Builder().baseUrl(servidor.url("/")).addConverterFactory(GsonConverterFactory.create()).build().create(ApiService::class.java)
        cuenta = CuentaRepository(api, ApiPublicacionesRepository(api, CachePrueba()), SesionPrueba().also { it.guardar("prueba") }, chatLocal = almacen)
    }

    // Se deja terminar lo que sigue en vuelo antes de apagar el servidor: una llamada cancelada a mitad de camino
    // hace que Retrofit lance un error interno de corrutinas que contamina la prueba siguiente.
    @After fun cerrar() { runBlocking { kotlinx.coroutines.delay(400) }; servidor.shutdown(); Dispatchers.resetMain() }

    private suspend fun abrirChat(): ChatViewModel {
        val vm = ChatViewModel(cuenta, fotos, 9, intervaloMs = 3_600_000, local = almacen)
        withTimeout(5000) { vm.chat.first { !it.cargando } }
        return vm
    }

    private fun esperar(condicion: () -> Boolean) = runBlocking { withTimeout(5000) { while (!condicion()) kotlinx.coroutines.delay(10) } }

    @Test fun laListaSeGuardaYSinConexionSeMuestraLaCopia() = runBlocking {
        val conexion = ConversacionesViewModel(cuenta)
        withTimeout(5000) { conexion.estado.first { !it.cargando } }
        assertEquals(1, almacen.lista.size)
        sinRed = true
        val vm = ConversacionesViewModel(cuenta)
        val estado = withTimeout(5000) { vm.estado.first { !it.cargando } }
        assertTrue(estado.datos!!.desdeCache)
        assertNotNull(estado.datos!!.aviso)
        assertEquals(9L, estado.datos!!.datos.single().id)
    }

    @Test fun sinConexionYSinCopiaSeMuestraElError() = runBlocking {
        sinRed = true
        val vm = ConversacionesViewModel(cuenta)
        val estado = withTimeout(5000) { vm.estado.first { !it.cargando } }
        assertNotNull(estado.error)
    }

    @Test fun elChatAbreConLoGuardadoAunSinConexion() = runBlocking {
        almacen.mensajesPorConversacion[9] = listOf(MensajeChat(1, 7, "Beto", "hola guardado", null, null))
        sinRed = true
        val vm = abrirChat()
        assertEquals(listOf("hola guardado"), vm.chat.value.mensajes.map { it.texto })
        esperar { vm.chat.value.sinConexion }
        assertTrue(vm.chat.value.sinConexion)
        assertEquals(null, vm.chat.value.error)
        vm.detenerSondeo()
    }

    @Test fun unMensajeEscritoSinConexionQuedaEnLaColaYSeEnviaUnaSolaVezAlVolver() = runBlocking {
        val vm = abrirChat()
        sinRed = true
        var limpio = false
        vm.enviar("  Ya voy  ") { limpio = true }
        esperar { vm.chat.value.pendientes.isNotEmpty() }
        assertTrue(limpio)
        assertEquals(listOf("Ya voy"), almacen.cola.map { it.texto })

        sinRed = false
        vm.reintentar()
        esperar { vm.chat.value.pendientes.isEmpty() && vm.chat.value.mensajes.isNotEmpty() }
        assertTrue(almacen.cola.isEmpty())
        assertEquals("El texto se envía una sola vez", 1, cuerpos.count { it.contains("Ya voy") })
        assertEquals(1, vm.chat.value.mensajes.size)
        // Una segunda consulta no vuelve a enviar nada.
        val antes = cuerpos.size
        vm.reintentar()
        esperar { llamadas.count { it == "GET /api/conversaciones/9/mensajes" } >= 3 }
        assertEquals(antes, cuerpos.size)
        vm.detenerSondeo()
    }

    @Test fun dosMensajesSinConexionSeEnvianEnOrden() = runBlocking {
        val vm = abrirChat()
        sinRed = true
        vm.enviar("primero"); esperar { vm.chat.value.pendientes.size == 1 }
        vm.enviar("segundo"); esperar { vm.chat.value.pendientes.size == 2 }
        sinRed = false
        cuerpos.clear()
        vm.reintentar()
        esperar { vm.chat.value.pendientes.isEmpty() && vm.chat.value.mensajes.size == 2 }
        val enviados = cuerpos.filter { it.contains("texto") }
        assertEquals(2, enviados.size)
        assertTrue(enviados[0].contains("primero") && enviados[1].contains("segundo"))
        vm.detenerSondeo()
    }

    @Test fun siElServidorRechazaUnPendienteSeDescartaYSeAvisa() = runBlocking {
        val vm = abrirChat()
        sinRed = true
        vm.enviar("tarde"); esperar { vm.chat.value.pendientes.size == 1 }
        sinRed = false
        rechazar = 1
        vm.reintentar()
        esperar { vm.chat.value.pendientes.isEmpty() }
        assertTrue(almacen.cola.isEmpty())
        assertNotNull(vm.chat.value.aviso)
        assertTrue(vm.chat.value.mensajes.isEmpty())
        vm.detenerSondeo()
    }

    @Test fun cerrarSesionBorraElChatGuardado() = runBlocking {
        almacen.lista = listOf(conversacion())
        almacen.mensajesPorConversacion[9] = listOf(MensajeChat(1, 7, "Beto", "privado", null, null))
        almacen.agregarPendiente(9, "pendiente")
        cuenta.cerrarSesion()
        assertEquals(1, almacen.limpiado)
        assertTrue(almacen.lista.isEmpty() && almacen.mensajesPorConversacion.isEmpty() && almacen.cola.isEmpty())
        assertFalse(cuenta.estadoSesion.autenticado)
    }
}
