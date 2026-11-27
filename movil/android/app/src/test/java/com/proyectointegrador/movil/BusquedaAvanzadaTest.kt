package com.proyectointegrador.movil

import com.google.gson.Gson
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
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class FiltrosBusquedaTest {
    @Test fun sinFiltrosNoHayNadaActivo() {
        assertFalse(FiltrosAvanzados().activos)
        assertTrue(etiquetasDeFiltros(FiltrosAvanzados()).isEmpty())
        assertNull(errorDeFiltros(FiltrosAvanzados()))
    }

    @Test fun lasFechasDebenSerRealesYEnOrden() {
        assertTrue(fechaIsoValida("2026-11-17"))
        assertFalse(fechaIsoValida("2026-02-30"))
        assertFalse(fechaIsoValida("17/11/2026"))
        assertEquals("La fecha «desde» no es válida.", errorDeFiltros(FiltrosAvanzados(desde = "2026-02-30")))
        assertEquals("«Desde» no puede ser posterior a «hasta».", errorDeFiltros(FiltrosAvanzados(desde = "2026-11-20", hasta = "2026-11-01")))
        assertNull("Un solo extremo es válido", errorDeFiltros(FiltrosAvanzados(desde = "2026-11-01")))
        assertNull("Un día es un rango válido", errorDeFiltros(FiltrosAvanzados(desde = "2026-11-01", hasta = "2026-11-01")))
    }

    @Test fun laCercaniaNecesitaCoordenadasYUnRadioRazonable() {
        assertEquals("La ubicación de cercanía está incompleta.", errorDeFiltros(FiltrosAvanzados(latitud = -12.0)))
        assertEquals("Las coordenadas de cercanía no son válidas.", errorDeFiltros(FiltrosAvanzados(latitud = 95.0, longitud = 0.0)))
        assertEquals("El radio debe estar entre 0.1 y 50 km.", errorDeFiltros(FiltrosAvanzados(latitud = -12.0, longitud = -77.0, radioKm = 80.0)))
        assertNull(errorDeFiltros(FiltrosAvanzados(latitud = -12.0, longitud = -77.0, radioKm = 2.0)))
    }

    @Test fun cadaFiltroActivoTieneSuChipYSeQuitaSolo() {
        val f = FiltrosAvanzados(ubicacion = "Biblioteca", desde = "2026-11-01", hasta = "2026-11-30", latitud = -12.0, longitud = -77.0, radioKm = 0.5)
        val etiquetas = etiquetasDeFiltros(f).toMap()
        assertEquals("Lugar: Biblioteca", etiquetas["ubicacion"])
        assertEquals("Del 2026-11-01 al 2026-11-30", etiquetas["fechas"])
        assertEquals("Cerca de mí (500 m)", etiquetas["cerca"])
        val sinFechas = sinFiltro(f, "fechas")
        assertEquals("", sinFechas.desde); assertEquals("", sinFechas.hasta)
        assertEquals("Biblioteca", sinFechas.ubicacion); assertNotNull(sinFechas.latitud)
        assertNull(sinFiltro(f, "cerca").latitud)
        assertEquals(f, sinFiltro(f, "inexistente"))
    }

    @Test fun elRadioSeFormateaEnMetrosOKilometros() {
        assertEquals("500 m", formatoRadio(0.5))
        assertEquals("1 km", formatoRadio(1.0))
        assertEquals("2 km", formatoRadio(2.0))
        assertEquals("2.5 km", formatoRadio(2.5))
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class BusquedaAvanzadaTest {
    private lateinit var servidor: MockWebServer
    private lateinit var publicaciones: ApiPublicacionesRepository

    @Before fun preparar() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        servidor = MockWebServer().also { it.start() }
        val api = Retrofit.Builder().baseUrl(servidor.url("/")).addConverterFactory(GsonConverterFactory.create()).build().create(ApiService::class.java)
        publicaciones = ApiPublicacionesRepository(api, CachePrueba())
    }

    @After fun cerrar() { servidor.shutdown(); Dispatchers.resetMain() }

    private fun responder(dato: Any, codigo: Int = 200) { servidor.enqueue(MockResponse().setResponseCode(codigo).setBody(Gson().toJson(dato))) }

    @Test fun laAppEnviaLosMismosParametrosQueLaWeb() = runBlocking {
        responder(listOf(Objeto(id = 5, nombre = "Mochila")))
        val filtros = FiltrosAvanzados("Biblioteca", "2026-11-01", "2026-11-30", -12.0464, -77.0428, 2.0)
        val resultado = publicaciones.buscarAvanzado("mochila", "PERDIDO", 3L, filtros).first()
        assertEquals(5L, resultado.datos.single().id)
        val url = servidor.takeRequest().requestUrl!!
        assertEquals("/api/objetos/buscar", url.encodedPath)
        assertEquals("mochila", url.queryParameter("q")); assertEquals("PERDIDO", url.queryParameter("tipo")); assertEquals("3", url.queryParameter("categoriaId"))
        assertEquals("Biblioteca", url.queryParameter("ubicacion"))
        assertEquals("2026-11-01", url.queryParameter("desde")); assertEquals("2026-11-30", url.queryParameter("hasta"))
        assertEquals("-12.0464", url.queryParameter("latitud")); assertEquals("-77.0428", url.queryParameter("longitud"))
        assertEquals("2.0", url.queryParameter("radioKm"))
    }

    @Test fun sinCercaniaNoSeEnviaElRadio() = runBlocking {
        responder(emptyList<Objeto>())
        publicaciones.buscarAvanzado(null, null, null, FiltrosAvanzados(ubicacion = "Aulas", radioKm = 5.0)).first()
        val url = servidor.takeRequest().requestUrl!!
        assertEquals("Aulas", url.queryParameter("ubicacion"))
        assertNull(url.queryParameter("radioKm")); assertNull(url.queryParameter("latitud")); assertNull(url.queryParameter("desde"))
    }

    @Test fun unRangoInvalidoNoConsultaAlServidor() = runBlocking {
        responder(emptyList<Objeto>()) // la carga inicial
        val vm = ObjetosViewModel(publicaciones)
        vm.buscarActual()
        withTimeout(5000) { vm.state.first { !it.cargando } }
        val antes = servidor.requestCount
        vm.aplicarAvanzados(FiltrosAvanzados(desde = "2026-11-20", hasta = "2026-11-01"))
        assertEquals("«Desde» no puede ser posterior a «hasta».", vm.busqueda.value.errorFiltros)
        assertFalse("Los filtros inválidos no se guardan", vm.busqueda.value.avanzados.activos)
        assertEquals(antes, servidor.requestCount)
    }

    @Test fun unFiltroValidoSeGuardaYSeQuitaDesdeSuChip() = runBlocking {
        responder(emptyList<Objeto>()); responder(emptyList<Objeto>()); responder(emptyList<Objeto>())
        val vm = ObjetosViewModel(publicaciones)
        vm.aplicarAvanzados(FiltrosAvanzados(ubicacion = "Biblioteca"))
        withTimeout(5000) { vm.state.first { !it.cargando } }
        assertEquals("Biblioteca", vm.busqueda.value.avanzados.ubicacion); assertNull(vm.busqueda.value.errorFiltros)
        vm.quitarFiltro("ubicacion")
        withTimeout(5000) { vm.state.first { !it.cargando } }
        assertFalse(vm.busqueda.value.avanzados.activos)
    }
}
