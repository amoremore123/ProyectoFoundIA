package com.proyectointegrador.movil

import com.proyectointegrador.movil.domain.mensajeErrorDictado
import org.junit.Assert.assertTrue
import org.junit.Test

class VozTest {
    @Test fun cadaErrorDelReconocedorTieneUnMensajeQueOrientaALaPersona() {
        assertTrue(mensajeErrorDictado(2).contains("conexión"))
        assertTrue(mensajeErrorDictado(6).contains("No te escuché"))
        assertTrue(mensajeErrorDictado(7).contains("No entendí"))
        assertTrue(mensajeErrorDictado(9).contains("permiso"))
        assertTrue(mensajeErrorDictado(3).contains("micrófono"))
    }

    @Test fun unCodigoDesconocidoNuncaDejaElMensajeVacio() {
        assertTrue(mensajeErrorDictado(99).isNotBlank())
        assertTrue(mensajeErrorDictado(-1).isNotBlank())
    }
}
