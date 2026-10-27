package com.proyectointegrador.movil
import com.proyectointegrador.movil.domain.*
import org.junit.Test
import org.junit.Assert.*
class EnlacesTest {
    @Test fun enlacePublicoNoIncluyeDatosDeSesion() {
        assertEquals("https://foundia.example/objeto/12",enlacePublico("https://foundia.example/",12))
        for(base in listOf("javascript:alert(1)","https://persona:clave@foundia.example/","https://foundia.example/?token=privado"))
            assertTrue(runCatching {enlacePublico(base,12)}.isFailure)
        assertTrue(runCatching {enlacePublico("https://foundia.example/",0)}.isFailure)
    }
    @Test fun abreSoloTokensDelEnlacePropio() {
        val token="prueba_unica_1234567890"
        assertEquals(token,tokenDeRecuperacion("foundia://restablecer?token=$token","https://foundia.example/"))
        assertEquals(token,tokenDeRecuperacion("https://foundia.example/restablecer-password?token=$token","https://foundia.example/"))
        for(enlace in listOf("foundia://restablecer", "foundia://restablecer?token=corto", "foundia://otro?token=$token",
            "foundia://restablecer?token=$token&token=otro", "https://ajeno.example/restablecer-password?token=$token",
            "http://foundia.example/restablecer-password?token=$token", "foundia://restablecer?token=$token#dato"))
            assertNull(enlace,tokenDeRecuperacion(enlace,"https://foundia.example/"))
    }
    @Test fun avisoSoloPermiteNavegarALaPareja() {
        assertEquals("12-34",parejaDeEnlace("/coincidencias/12-34"))
        for(enlace in listOf(null,"https://ajeno.invalid/","/coincidencias/0-1","/coincidencias/1-2?token=privado","/admin/"))
            assertNull(parejaDeEnlace(enlace))
    }
}
