package com.proyectointegrador.movil

import com.proyectointegrador.movil.domain.admiteCoincidencias
import com.proyectointegrador.movil.domain.etiquetaEstadoObjeto
import com.proyectointegrador.movil.domain.ordenarCategorias
import com.proyectointegrador.movil.model.Categoria
import org.junit.Assert.*
import org.junit.Test

class EstadosTest {
    @Test fun elEstadoTecnicoSeMuestraEnEspanol() {
        assertEquals("En custodia", etiquetaEstadoObjeto("EN_CUSTODIA"))
        assertEquals("Activo", etiquetaEstadoObjeto("ACTIVO"))
        assertEquals("Recuperado", etiquetaEstadoObjeto("RECUPERADO"))
        assertEquals("Sin estado", etiquetaEstadoObjeto(null))
        assertEquals("Sin estado", etiquetaEstadoObjeto(""))
        assertEquals("Pendiente revision", etiquetaEstadoObjeto("PENDIENTE_REVISION"))
    }

    @Test fun soloUnObjetoPublicoPideCoincidencias() {
        assertTrue(admiteCoincidencias("ACTIVO"))
        assertTrue(admiteCoincidencias(null))
        assertFalse(admiteCoincidencias("EN_CUSTODIA"))
        assertFalse(admiteCoincidencias("RECUPERADO"))
        assertFalse(admiteCoincidencias("OCULTO"))
    }

    @Test fun lasCategoriasVanEnOrdenAlfabeticoConOtrosAlFinal() {
        val lista = listOf("Otros", "Ropa", "Álbum", "Accesorio", "Celular", "Documento", "Llaves", "Laptop", "Mochila")
            .mapIndexed { i, n -> Categoria(id = i.toLong() + 1, nombre = n) }
        val nombres = ordenarCategorias(lista).map { it.nombre }
        assertEquals(listOf("Accesorio", "Álbum", "Celular", "Documento", "Laptop", "Llaves", "Mochila", "Ropa", "Otros"), nombres)
        assertEquals("Los ids no cambian al ordenar", lista.map { it.id }.toSet(), ordenarCategorias(lista).map { it.id }.toSet())
    }
}
