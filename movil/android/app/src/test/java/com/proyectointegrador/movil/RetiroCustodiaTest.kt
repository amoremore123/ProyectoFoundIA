package com.proyectointegrador.movil

import com.proyectointegrador.movil.data.repository.comoErrorDatos
import com.proyectointegrador.movil.domain.esCodigoRetiro
import com.proyectointegrador.movil.domain.esEtiquetaCustodia
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.*
import org.junit.Test
import retrofit2.HttpException
import retrofit2.Response

class RetiroCustodiaTest {
    // Valores con la forma de los reales: JWT HS256 de 175 caracteres y token opaco de 43.
    private val jwt = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9.eyJzdWIiOiJGTkQtMjAyNi0wMDA5Iiwib2JqZXRvIjoxOH0.k3Zq1v8mT0aXn5RjQw9bYc2LdEoHfUgPsIiVtN4xAyM"
    private val retiro = "mN3vQ8xTz1LkP0aYcR7wHdJ2uEoBsFgXiVtKnA4yZqM"

    @Test fun laEtiquetaAceptaElEnlaceDelQrYElTokenSolo() {
        assertTrue(esEtiquetaCustodia(jwt))
        assertTrue(esEtiquetaCustodia("http://localhost:18273/custodia/verificar?t=$jwt"))
        assertTrue(esEtiquetaCustodia("https://foundia.example/custodia/verificar?x=1&t=$jwt"))
        assertTrue(esEtiquetaCustodia("  $jwt  "))
    }

    @Test fun laEtiquetaRechazaUnQrDeRetiroYOtrosContenidos() {
        assertFalse("Un QR de retiro cabe por su largo, pero no es una etiqueta", esEtiquetaCustodia(retiro))
        assertFalse(esEtiquetaCustodia(""))
        assertFalse(esEtiquetaCustodia("hola"))
        assertFalse(esEtiquetaCustodia("http://localhost:18273/custodia/verificar"))
        assertFalse(esEtiquetaCustodia("http://localhost:18273/custodia/verificar?t=corto"))
        assertFalse(esEtiquetaCustodia("A".repeat(501)))
    }

    @Test fun elCodigoDeRetiroRechazaLaEtiquetaYContenidosAjenos() {
        assertTrue(esCodigoRetiro(retiro))
        assertTrue(esCodigoRetiro("  $retiro "))
        assertFalse("Una etiqueta tiene puntos y supera 64 caracteres", esCodigoRetiro(jwt))
        assertFalse(esCodigoRetiro("http://localhost:18273/custodia/verificar?t=$jwt"))
        assertFalse(esCodigoRetiro(""))
        assertFalse(esCodigoRetiro("corto"))
    }

    private fun http(codigo: Int, cuerpo: String) =
        HttpException(Response.error<Any>(codigo, cuerpo.toResponseBody("application/json".toMediaType())))

    @Test fun losRechazosDeDjangoMuestranSuMensajeEnEspanol() {
        val casos = mapOf(
            400 to "La etiqueta de custodia no es válida.",
            404 to "El QR de retiro no es válido.",
            409 to "El QR de retiro ya fue usado.",
        )
        for ((codigo, texto) in casos) {
            val error = http(codigo, """{"detail":"$texto"}""").comoErrorDatos()
            assertEquals(texto, error.mensaje)
            assertEquals(codigo, error.estadoHttp)
        }
    }

    @Test fun elFormatoDeSpringYLosTextosEnInglesDeDjangoNoCambian() {
        assertEquals("No disponible", http(409, """{"mensaje":"No disponible"}""").comoErrorDatos().mensaje)
        assertEquals("El recurso no está disponible o fue retirado.", http(404, """{"mensaje":"otro"}""").comoErrorDatos().mensaje)
        assertEquals("Tu cuenta no tiene permiso para esta acción.",
            http(403, """{"detail":"You do not have permission to perform this action."}""").comoErrorDatos().mensaje)
        assertEquals("No se pudo completar la operación. Reintenta.", http(500, "<html>error</html>").comoErrorDatos().mensaje)
    }
}
