package com.proyectointegrador.service;

import com.proyectointegrador.entity.Categoria;
import com.proyectointegrador.entity.Objeto;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import java.time.LocalDate;
import static org.assertj.core.api.Assertions.assertThat;

class CoincidenciaCriteriosTest {
    private final CoincidenciaService motor = new CoincidenciaService(null, null, null);

    @ParameterizedTest
    @CsvSource({"0,15", "3,15", "4,10", "7,10", "8,5", "30,5", "31,0"})
    void intervalosDeFechaSonSimetricosYRespetanSusExtremos(int dias, int esperado) {
        var primero = objeto("Mochila", "Biblioteca");
        var segundo = objeto("Mochila", "Biblioteca");
        segundo.setFechaObjeto(primero.getFechaObjeto().plusDays(dias));
        assertThat(motor.criterios(primero, segundo)).containsEntry("fecha", esperado);
        assertThat(motor.criterios(segundo, primero)).isEqualTo(motor.criterios(primero, segundo));
    }

    @ParameterizedTest
    @CsvSource({"40,BAJA", "54,BAJA", "55,MEDIA", "79,MEDIA", "80,ALTA", "100,ALTA"})
    void nivelesTienenLimitesDocumentados(int puntos, String nivel) {
        assertThat(motor.nivel(puntos)).isEqualTo(nivel);
    }

    @Test
    void nombresIgnoranTildesMayusculasPuntuacionYRepeticiones() {
        var primero = objeto("  LLÁVES, azules azules! ", "Cómputo");
        var segundo = objeto("llaves azules", "computo");
        assertThat(motor.puntuar(primero, segundo)).isEqualTo(100);
    }

    @Test
    void nombresParcialesUsanJaccardYUbicacionContenidaTieneDiezPuntos() {
        var primero = objeto("Llaves azules", "Biblioteca");
        var segundo = objeto("Llaves rojas", "Biblioteca central");
        assertThat(motor.criterios(primero, segundo)).containsEntry("nombre", 13).containsEntry("ubicacion", 10);
        assertThat(motor.puntuar(primero, segundo)).isEqualTo(63);
    }

    @Test
    void categoriasDistintasOAusentesNoSumanPuntos() {
        var primero = objeto("Mochila", "Biblioteca");
        var segundo = objeto("Mochila", "Biblioteca");
        segundo.getCategoria().setId(2L);
        assertThat(motor.criterios(primero, segundo)).containsEntry("categoria", 0);
        primero.setCategoria(null);
        assertThat(motor.criterios(primero, segundo)).containsEntry("categoria", 0);
    }

    @Test
    void datosOpcionalesIncompletosNoProducenPuntosInventados() {
        var categoria = new Categoria();
        categoria.setId(1L);
        var perdido = new Objeto();
        perdido.setCategoria(categoria);
        var encontrado = new Objeto();
        encontrado.setCategoria(categoria);
        encontrado.setNombre("Llaves");
        encontrado.setUbicacion("Biblioteca");
        encontrado.setFechaObjeto(LocalDate.now());
        var criterios = motor.criterios(perdido, encontrado);
        assertThat(criterios).containsEntry("nombre", 0).containsEntry("categoria", 25)
                .containsEntry("ubicacion", 0).containsEntry("fecha", 0);
    }

    private Objeto objeto(String nombre, String ubicacion) {
        var categoria = new Categoria();
        categoria.setId(1L);
        var objeto = new Objeto();
        objeto.setNombre(nombre);
        objeto.setUbicacion(ubicacion);
        objeto.setCategoria(categoria);
        objeto.setFechaObjeto(LocalDate.of(2026, 10, 1));
        return objeto;
    }
}
