package com.proyectointegrador.movil

import com.google.gson.Gson
import com.proyectointegrador.movil.model.Documentos
import com.proyectointegrador.movil.ui.components.avisoDocumentos
import com.proyectointegrador.movil.ui.components.fuenteOficialTecsup
import org.junit.Assert.*
import org.junit.Test

class FuentesLegalesTest {
    @Test fun reconoceSoloElSitioHttpsOficial() {
        assertTrue(fuenteOficialTecsup("https://www.tecsup.edu.pe/terminos-y-condiciones/"))
        for (url in listOf("http://www.tecsup.edu.pe/", "javascript:alert(1)",
            "https://www.tecsup.edu.pe.ejemplo.invalid/", "https://usuario@www.tecsup.edu.pe/", "https://www.tecsup.edu.pe:444/")) {
            assertFalse(url, fuenteOficialTecsup(url))
        }
    }

    @Test fun consumeFuentesYAvisoSinInventarUnaAprobacion() {
        val docs = Gson().fromJson("""{"version":"foundia-tecsup-2026-11-05-v2","estado":"FUENTES_OFICIALES_TECSUP_CON_AVISO_FOUNDIA",
            "terminos":"Texto completo","privacidad":"Texto completo","sha256":"huella",
            "aviso":"La referencia no autoriza publicidad ni acredita aprobación institucional.",
            "fuentes":[{"id":"terminos-tecsup","titulo":"Términos de Tecsup","url":"https://www.tecsup.edu.pe/terminos-y-condiciones/",
            "consultadoEl":"2026-11-05","sha256Original":"original","sha256Texto":"texto"}]}""", Documentos::class.java)
        assertEquals(1, docs.fuentes!!.size)
        assertEquals("2026-11-05", docs.fuentes!!.single().consultadoEl)
        assertEquals(docs.aviso, avisoDocumentos(docs))
    }

    @Test fun mantieneLecturaDeLaRespuestaAnteriorSinCamposNuevos() {
        val docs = Gson().fromJson("""{"version":"v1","estado":"TEXTO_DEL_PROYECTO_PENDIENTE_REVISION_INSTITUCIONAL",
            "terminos":"Términos","privacidad":"Privacidad","sha256":"huella"}""", Documentos::class.java)
        assertTrue(docs.fuentes.orEmpty().isEmpty())
        assertTrue(avisoDocumentos(docs).contains("pendientes de revisión institucional"))
    }
}
