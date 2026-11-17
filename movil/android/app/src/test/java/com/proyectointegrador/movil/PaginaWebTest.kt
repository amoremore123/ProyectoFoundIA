package com.proyectointegrador.movil

import com.proyectointegrador.movil.domain.PAGINAS_WEB_PERMITIDAS
import com.proyectointegrador.movil.domain.extraerTokenEtiqueta
import com.proyectointegrador.movil.domain.navegacionPermitida
import com.proyectointegrador.movil.domain.urlPaginaWeb
import org.junit.Assert.*
import org.junit.Test

class PaginaWebTest {
    private val base = "http://127.0.0.1:18273/"
    private val token = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJGTkQtMjAyNi0wMDA5Iiwib2JqZXRvIjoxOH0.k3Zq1v8mT0aXn5RjQw9bYc2LdEoHfUgPsIiVtN4xAyM"

    @Test fun losTerminosYLaPrivacidadAbrenEnModoEmbebido() {
        assertEquals("http://127.0.0.1:18273/terminos?embebido=1", urlPaginaWeb(base, "terminos"))
        assertEquals("http://127.0.0.1:18273/privacidad?embebido=1", urlPaginaWeb(base, "privacidad"))
    }

    @Test fun laEtiquetaNecesitaSuTokenFirmadoYLoCodifica() {
        assertEquals("http://127.0.0.1:18273/custodia/verificar?t=$token&embebido=1", urlPaginaWeb(base, "etiqueta", token))
        val enlace = "http://localhost:5173/custodia/verificar?t=$token"
        assertEquals(token, extraerTokenEtiqueta(enlace))
        assertEquals("http://127.0.0.1:18273/custodia/verificar?t=$token&embebido=1", urlPaginaWeb(base, "etiqueta", enlace))
        assertNull(urlPaginaWeb(base, "etiqueta"))
        assertNull(urlPaginaWeb(base, "etiqueta", "no-es-una-etiqueta"))
    }

    @Test fun cualquierOtraClaveOBaseInvalidaNoAbreNada() {
        assertNull(urlPaginaWeb(base, "admin"))
        assertNull(urlPaginaWeb(base, "../panel"))
        assertNull(urlPaginaWeb("javascript:alert(1)", "terminos"))
        assertNull(urlPaginaWeb("http://usuario:clave@127.0.0.1:18273/", "terminos"))
        assertEquals(setOf("terminos", "privacidad", "etiqueta"), PAGINAS_WEB_PERMITIDAS.keys)
    }

    @Test fun elWebViewSoloNavegaEnElMismoOrigen() {
        assertTrue(navegacionPermitida(base, "http://127.0.0.1:18273/terminos?embebido=1"))
        assertTrue(navegacionPermitida(base, "http://127.0.0.1:18273/privacidad"))
        assertFalse(navegacionPermitida(base, "http://127.0.0.1:9999/privacidad"))
        assertFalse(navegacionPermitida(base, "https://127.0.0.1:18273/privacidad"))
        assertFalse(navegacionPermitida(base, "http://evil.example/terminos"))
        assertFalse(navegacionPermitida(base, "http://127.0.0.1.evil.example:18273/"))
        assertFalse(navegacionPermitida(base, "javascript:alert(1)"))
        assertFalse(navegacionPermitida(base, "file:///data/data/app/secreto"))
        assertFalse(navegacionPermitida(base, "http://usuario@127.0.0.1:18273/terminos"))
    }
}
