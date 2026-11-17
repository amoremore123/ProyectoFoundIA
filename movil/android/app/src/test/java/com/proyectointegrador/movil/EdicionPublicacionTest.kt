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
import okhttp3.mockwebserver.RecordedRequest
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.text.SimpleDateFormat
import java.util.Locale

class ValidacionPublicacionTest {
    private fun ok(nombre: String = "Mochila", descripcion: String = "Azul", categoria: Boolean = true, ubicacion: String = "Biblioteca",
                   lat: Double? = null, lon: Double? = null, fecha: String = "2026-10-20", tipo: String = "PERDIDO") =
        validarPublicacion(nombre, descripcion, categoria, ubicacion, lat, lon, fecha, tipo, SimpleDateFormat("yyyy-MM-dd", Locale.ROOT).parse("2026-11-17")!!)

    @Test fun unaPublicacionCorrectaNoTieneErrores() { assertTrue(ok().isEmpty()) }

    @Test fun cadaCampoInvalidoTieneSuMensaje() {
        assertEquals(setOf("nombre"), ok(nombre = " ").keys)
        assertEquals(setOf("nombre"), ok(nombre = "x".repeat(151)).keys)
        assertEquals(setOf("descripcion"), ok(descripcion = "").keys)
        assertEquals(setOf("categoria"), ok(categoria = false).keys)
        assertEquals(setOf("ubicacion"), ok(ubicacion = "x".repeat(256)).keys)
        assertEquals(setOf("ubicacion"), ok(lat = -12.0).keys)
        assertEquals(setOf("fecha"), ok(fecha = "2026-11-18").keys)
        assertEquals(setOf("fecha"), ok(fecha = "2026-02-30").keys)
        assertEquals(setOf("tipo"), ok(tipo = "OTRO").keys)
        assertTrue("La fecha de hoy es válida", ok(fecha = "2026-11-17").isEmpty())
    }

    @Test fun lasReglasDeEdicionYEliminacionSonLasDeLaWeb() {
        assertTrue(puedeEditarPublicacion("ACTIVO")); assertTrue(puedeEditarPublicacion("RECUPERADO")); assertTrue(puedeEditarPublicacion("OCULTO"))
        assertFalse(puedeEditarPublicacion("EN_CUSTODIA")); assertFalse(puedeEditarPublicacion("ELIMINADO")); assertFalse(puedeEditarPublicacion(null))
        assertTrue(puedeEliminarPublicacion("ACTIVO")); assertTrue(puedeEliminarPublicacion("RECUPERADO"))
        assertFalse(puedeEliminarPublicacion("OCULTO")); assertFalse(puedeEliminarPublicacion("EN_CUSTODIA")); assertFalse(puedeEliminarPublicacion("ELIMINADO"))
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class EdicionPublicacionTest {
    private lateinit var servidor: MockWebServer
    private lateinit var cuenta: CuentaRepository
    private lateinit var publicaciones: ApiPublicacionesRepository
    private val categoria = Categoria(3, "Mochila", null, true)
    private val llamadas = mutableListOf<String>()
    private val cuerpos = mutableListOf<String>()
    private var estadoObjeto = "ACTIVO"
    private var propios: List<Objeto> = emptyList()

    private fun objeto(estado: String = estadoObjeto) = Objeto(id = 41, nombre = "Mochila azul", descripcion = "Con llavero", ubicacion = "Biblioteca",
        fechaObjeto = "2026-11-10", tipo = "PERDIDO", estado = estado, categoria = ObjetoCategoria(categoria.id, categoria.nombre), latitud = -12.0464, longitud = -77.0428)

    @Before fun preparar() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        servidor = MockWebServer()
        // Cada ruta tiene su respuesta: el orden de llegada de las peticiones no importa.
        servidor.dispatcher = object : Dispatcher() {
            override fun dispatch(request: RecordedRequest): MockResponse {
                val ruta = request.requestUrl!!.encodedPath
                llamadas += "${request.method} $ruta"
                if (request.method == "PUT" || request.method == "POST") cuerpos += request.body.readUtf8()
                fun json(d: Any) = MockResponse().setResponseCode(200).setBody(Gson().toJson(d))
                return when {
                    request.method == "GET" && ruta == "/api/objetos/41" -> json(objeto())
                    request.method == "GET" && ruta == "/api/categorias" -> json(listOf(categoria))
                    request.method == "GET" && ruta == "/api/perfil/objetos" -> json(propios)
                    request.method == "PUT" && ruta == "/api/objetos/41" -> json(objeto())
                    request.method == "DELETE" && ruta == "/api/objetos/41" -> { propios = emptyList(); MockResponse().setResponseCode(204) }
                    else -> MockResponse().setResponseCode(404)
                }
            }
        }
        servidor.start()
        val api = Retrofit.Builder().baseUrl(servidor.url("/")).addConverterFactory(GsonConverterFactory.create()).build().create(ApiService::class.java)
        publicaciones = ApiPublicacionesRepository(api, CachePrueba())
        cuenta = CuentaRepository(api, publicaciones, SesionPrueba().also { it.guardar("prueba") })
    }

    @After fun cerrar() { servidor.shutdown(); Dispatchers.resetMain() }

    private suspend fun editor(estado: String = "ACTIVO"): EditarPublicacionViewModel {
        estadoObjeto = estado
        val vm = EditarPublicacionViewModel(cuenta, publicaciones, 41)
        withTimeout(5000) { vm.estado.first { !it.cargando } }
        withTimeout(5000) { vm.categorias.first { it.isNotEmpty() } }
        vm.iniciar(vm.estado.value.datos!!)
        return vm
    }

    @Test fun elFormularioSeLlenaConLosDatosDelServidor() = runBlocking {
        val vm = editor()
        assertEquals("Mochila azul", vm.form.value.nombre); assertEquals("Con llavero", vm.form.value.descripcion)
        assertEquals(3L, vm.form.value.categoriaId); assertEquals("2026-11-10", vm.form.value.fecha)
        assertTrue(vm.editable)
    }

    @Test fun guardarEnviaUnPutConLosCambiosYLasCoordenadas() = runBlocking {
        val vm = editor()
        vm.editar("nombre", "Mochila azul con cierre")
        vm.guardar()
        withTimeout(5000) { vm.evento.first { it == "guardado" } }
        assertTrue(llamadas.contains("PUT /api/objetos/41"))
        val cuerpo = JsonParser.parseString(cuerpos.last()).asJsonObject
        assertEquals("Mochila azul con cierre", cuerpo["nombre"].asString)
        assertEquals(-12.0464, cuerpo["latitud"].asDouble, 1e-9)
        assertEquals(3L, cuerpo["categoriaId"].asLong)
    }

    @Test fun escribirOtroLugarDescartaLasCoordenadasAnteriores() = runBlocking {
        val vm = editor()
        vm.editar("ubicacion", "Aula 204")
        assertNull(vm.form.value.latitud); assertNull(vm.form.value.longitud)
    }

    @Test fun unFormularioInvalidoNoLlamaAlServidor() = runBlocking {
        val vm = editor()
        vm.editar("nombre", "")
        vm.guardar()
        assertNotNull(vm.form.value.errores["nombre"])
        assertEquals("Revisa los campos señalados.", vm.accion.value.error)
        assertFalse(llamadas.any { it.startsWith("PUT") })
    }

    @Test fun unaPublicacionEnCustodiaNoSeEdita() = runBlocking {
        val vm = editor("EN_CUSTODIA")
        assertFalse(vm.editable)
        vm.guardar()
        assertEquals("Esta publicación ya no se puede editar.", vm.accion.value.error)
        assertFalse(llamadas.any { it.startsWith("PUT") })
    }

    @Test fun eliminarPideConfirmacionYBorraSoloAlConfirmar() = runBlocking {
        propios = listOf(objeto())
        val vm = PropiosViewModel(cuenta)
        withTimeout(5000) { vm.estado.first { !it.cargando } }
        vm.pedirEliminar(objeto())
        assertNotNull(vm.porEliminar.value)
        vm.cancelarEliminar()
        assertNull(vm.porEliminar.value)
        assertFalse("Cancelar no llama al servidor", llamadas.any { it.startsWith("DELETE") })
        vm.pedirEliminar(objeto())
        vm.confirmarEliminar()
        withTimeout(5000) { vm.accion.first { it.mensaje != null && !it.ocupada } }
        // La lista se recarga después de borrar: se espera a que termine para no dejar corrutinas sueltas.
        withTimeout(5000) { vm.estado.first { !it.cargando && it.datos?.isEmpty() == true } }
        assertTrue(llamadas.contains("DELETE /api/objetos/41"))
        assertNull(vm.porEliminar.value)
        assertEquals("Publicación eliminada. Se conserva en tu historial.", vm.accion.value.mensaje)
    }

    @Test fun unaPublicacionOcultaNoSePuedeEliminar() = runBlocking {
        propios = listOf(objeto("OCULTO"))
        val vm = PropiosViewModel(cuenta)
        withTimeout(5000) { vm.estado.first { !it.cargando } }
        vm.pedirEliminar(objeto("OCULTO"))
        assertNull(vm.porEliminar.value)
    }
}
