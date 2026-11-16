package com.proyectointegrador.movil

import com.proyectointegrador.movil.data.repository.AsistenteRepository
import com.proyectointegrador.movil.model.RespuestaFindi
import com.proyectointegrador.movil.ui.viewmodel.AsistenteViewModel
import kotlinx.coroutines.*
import kotlinx.coroutines.test.*
import org.junit.*
import org.junit.Assert.*
import java.io.IOException

@OptIn(ExperimentalCoroutinesApi::class)
class AsistenteViewModelTest {
    @Before fun preparar() { Dispatchers.setMain(UnconfinedTestDispatcher()) }
    @After fun cerrar() { Dispatchers.resetMain() }
    @Test fun evitaEnviosDoblesMientrasConsultaYConservaIdsUnicos() = runTest {
        val espera = CompletableDeferred<RespuestaFindi>()
        var llamadas = 0
        val vm = AsistenteViewModel(object : AsistenteRepository {
            override suspend fun responder(mensaje: String): RespuestaFindi { llamadas++; return espera.await() }
        })
        vm.enviar("mochila"); vm.enviar("otra consulta")
        assertTrue(vm.estado.value.pensando)
        assertEquals(1, llamadas)
        espera.complete(RespuestaFindi("Sin resultados")); advanceUntilIdle()
        assertFalse(vm.estado.value.pensando)
        assertEquals(3, vm.estado.value.mensajes.size)
        val ids = vm.estado.value.mensajes.map { it.id }
        assertEquals(ids.size, ids.distinct().size)
    }
    @Test fun elErrorPermiteReintentarSinDuplicarElMensajePropio() = runTest {
        var fallo = true
        val vm = AsistenteViewModel(object : AsistenteRepository {
            override suspend fun responder(mensaje: String): RespuestaFindi {
                if (fallo) throw IOException("sin conexión")
                return RespuestaFindi("Respuesta real")
            }
        })
        vm.enviar("llaves"); advanceUntilIdle()
        assertEquals("llaves", vm.estado.value.mensajes.last().reintentar)
        fallo = false; vm.enviar("llaves", repetir = true); advanceUntilIdle()
        assertEquals(1, vm.estado.value.mensajes.count { it.propio })
        assertEquals("Respuesta real", vm.estado.value.mensajes.last().texto)
    }
    @Test fun ayudaYValidacionNoNecesitanUnaConsultaExterna() = runTest {
        val vm = AsistenteViewModel(object : AsistenteRepository {
            override suspend fun responder(mensaje: String): RespuestaFindi = error("No debe consultar")
        })
        vm.enviar(" "); vm.enviar("x".repeat(301)); vm.comoFunciona()
        assertEquals(2, vm.estado.value.mensajes.size)
        assertTrue(vm.estado.value.mensajes.last().texto.contains("lugar seguro"))
    }
}
