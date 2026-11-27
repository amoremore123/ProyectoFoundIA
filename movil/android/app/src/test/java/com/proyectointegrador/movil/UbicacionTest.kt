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
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.test.resetMain
import okhttp3.mockwebserver.MockResponse
import okhttp3.mockwebserver.MockWebServer
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory

class UbicacionDominioTest {
    @Test fun lasCoordenadasFueraDeRangoNoSonValidas() {
        assertTrue(coordenadasValidas(-12.0464, -77.0428))
        assertFalse(coordenadasValidas(91.0, 0.0))
        assertFalse(coordenadasValidas(0.0, -181.0))
        assertFalse(coordenadasValidas(null, 10.0))
        assertFalse(coordenadasValidas(10.0, null))
        assertFalse(coordenadasValidas(Double.NaN, 10.0))
    }

    @Test fun lasCoordenadasSeRedondeanASieteDecimales() {
        assertEquals(-12.0464001, redondearCoordenada(-12.04640012345), 1e-9)
    }

    @Test fun sinDireccionElTextoUsaLasCoordenadas() {
        val c = Coordenadas(-12.04644, -77.04281)
        assertEquals("Ubicación actual (-12.04644, -77.04281)", textoUbicacionGps(null, c))
        assertEquals("Ubicación actual (-12.04644, -77.04281)", textoUbicacionGps("  ", c))
        assertEquals("Av. Cascanueces 2221, Santa Anita", textoUbicacionGps("  Av. Cascanueces 2221, Santa Anita ", c))
        assertEquals(255, textoUbicacionGps("x".repeat(400), c).length)
    }

    @Test fun elEnlaceDeMapasLlevaElPuntoYLaEtiqueta() {
        assertEquals("geo:-12.0464000,-77.0428000?q=-12.0464000,-77.0428000(Biblioteca%20central)", enlaceMapas(-12.0464, -77.0428, "Biblioteca central"))
    }

    @Test fun losLugaresDelCampusSonLosDeLaWeb() {
        assertEquals(listOf("Biblioteca", "Cafetería", "Laboratorios", "Talleres", "Aulas", "Auditorio"), LUGARES_CAMPUS)
    }
}

@OptIn(ExperimentalCoroutinesApi::class)
class UbicacionPublicarTest {
    private lateinit var servidor: MockWebServer
    private lateinit var cuenta: CuentaRepository
    private lateinit var publicaciones: ApiPublicacionesRepository

    @Before fun preparar() {
        Dispatchers.setMain(UnconfinedTestDispatcher())
        servidor = MockWebServer().also { it.start() }
        val api = Retrofit.Builder().baseUrl(servidor.url("/")).addConverterFactory(GsonConverterFactory.create()).build().create(ApiService::class.java)
        publicaciones = ApiPublicacionesRepository(api, CachePrueba())
        val sesion = SesionPrueba().also { it.guardar("prueba") }
        cuenta = CuentaRepository(api, publicaciones, sesion)
    }

    @After fun cerrar() { servidor.shutdown(); Dispatchers.resetMain() }

    private fun responder(dato: Any, codigo: Int = 200) { servidor.enqueue(MockResponse().setResponseCode(codigo).setBody(Gson().toJson(dato))) }

    private suspend fun publicador(): PublicarViewModel {
        responder(listOf(Categoria(1, "Documentos", null, true)))
        val vm = PublicarViewModel(cuenta, publicaciones)
        withTimeout(5000) { vm.estado.first { !it.cargando } }
        vm.editar("nombre", "Carné"); vm.editar("descripcion", "Objeto de prueba"); vm.editar("fecha", "2026-10-20"); vm.categoria(1)
        return vm
    }

    @Test fun unLugarDelCampusNoEnviaCoordenadas() = runBlocking {
        val vm = publicador()
        vm.ubicacionGps(Coordenadas(-12.0464, -77.0428), null)
        vm.lugarCampus("Biblioteca")
        assertNull(vm.form.value.latitud); assertNull(vm.form.value.longitud)
        assertEquals("Biblioteca", vm.form.value.ubicacion)
    }

    @Test fun elGpsSeEnviaConLaPublicacion() = runBlocking {
        val vm = publicador()
        vm.ubicacionGps(Coordenadas(-12.0464, -77.0428), "Av. Cascanueces 2221")
        assertEquals("Av. Cascanueces 2221", vm.form.value.ubicacion)
        responder(Objeto(id = 77), 201)
        vm.publicar()
        withTimeout(5000) { vm.accion.first { !it.ocupada } }
        servidor.takeRequest() // categorías
        val cuerpo = JsonParser.parseString(servidor.takeRequest().body.readUtf8()).asJsonObject
        assertEquals(-12.0464, cuerpo["latitud"].asDouble, 1e-9)
        assertEquals(-77.0428, cuerpo["longitud"].asDouble, 1e-9)
        assertEquals("Av. Cascanueces 2221", cuerpo["ubicacion"].asString)
    }

    @Test fun escribirElLugarBorraLasCoordenadasDelGps() = runBlocking {
        val vm = publicador()
        vm.ubicacionGps(Coordenadas(-12.0464, -77.0428), null)
        vm.editar("ubicacion", "Aula 204")
        assertNull(vm.form.value.latitud); assertNull(vm.form.value.longitud)
    }

    @Test fun quitarElGpsConservaElTextoPeroNoLasCoordenadas() = runBlocking {
        val vm = publicador()
        vm.ubicacionGps(Coordenadas(-12.0464, -77.0428), "Mi dirección")
        vm.quitarGps()
        assertEquals("Mi dirección", vm.form.value.ubicacion)
        assertNull(vm.form.value.latitud)
    }
}
