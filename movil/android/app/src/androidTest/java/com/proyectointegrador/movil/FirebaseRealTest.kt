package com.proyectointegrador.movil

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.firebase.FirebaseApp
import com.proyectointegrador.movil.data.push.crearServicioFirebase
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Comprueba Firebase con el proyecto real del equipo. Si el APK se compiló sin `google-services.json`
 * la prueba se omite (la app debe funcionar igual sin Firebase).
 */
@RunWith(AndroidJUnit4::class)
class FirebaseRealTest {
    private val contexto = InstrumentationRegistry.getInstrumentation().targetContext

    @Test fun conGoogleServicesElProyectoSeInicializaYDaUnTokenDePush() = runBlocking {
        assumeTrue("Sin google-services.json no hay proyecto de Firebase", FirebaseApp.getApps(contexto).isNotEmpty())
        val servicio = crearServicioFirebase(contexto)
        assertTrue(servicio.disponible)
        val token = withTimeout(60_000) { servicio.tokenPush() }
        assertTrue("FCM debe entregar un token", !token.isNullOrBlank() && token.length >= 100)
    }

    @Test fun sinSesionDeFirebaseSalirNoFalla() {
        val servicio = crearServicioFirebase(contexto)
        servicio.salir()
        servicio.borrarTokenPush()
    }
}
