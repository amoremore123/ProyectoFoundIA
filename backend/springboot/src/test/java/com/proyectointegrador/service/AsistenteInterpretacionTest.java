package com.proyectointegrador.service;

import com.proyectointegrador.entity.Categoria;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("H45 - motor local y validación de la respuesta de IA")
class AsistenteInterpretacionTest {

    private final AsistenteMotorLocal motor = new AsistenteMotorLocal();
    private final AsistenteIaValidator validador = new AsistenteIaValidator();

    private Categoria categoria(Long id, String nombre, String descripcion, boolean estado) {
        Categoria categoria = new Categoria();
        categoria.setId(id);
        categoria.setNombre(nombre);
        categoria.setDescripcion(descripcion);
        categoria.setEstado(estado);
        return categoria;
    }

    private final List<Categoria> categorias = List.of(
            categoria(1L, "Celular", "Teléfonos móviles", true),
            categoria(2L, "Mochila", "Bolsos y morrales", true),
            categoria(3L, "Documentos", "DNI, carné y credenciales", true),
            categoria(4L, "Desactivada", "No debe usarse", false));

    @Test
    @DisplayName("Interpreta una pérdida con tipo, categoría, lugar y color")
    void interpretaPerdida() {
        InterpretacionAsistente r = motor.interpretar(
                "Perdí mi celular azul en la biblioteca", categorias);

        assertThat(r.intencion()).isEqualTo("buscar");
        assertThat(r.tipoBuscado()).isEqualTo("ENCONTRADO");
        assertThat(r.categoriaId()).isEqualTo(1L);
        assertThat(r.ubicacion()).isEqualTo("biblioteca");
        assertThat(r.palabras()).contains("celular", "azul");
        assertThat(r.colores()).contains("azul");
    }

    @Test
    @DisplayName("Interpreta un hallazgo y busca entre las pérdidas")
    void interpretaHallazgo() {
        InterpretacionAsistente r = motor.interpretar(
                "Encontré unas llaves en el aula 204", categorias);

        assertThat(r.intencion()).isEqualTo("buscar");
        assertThat(r.tipoBuscado()).isEqualTo("PERDIDO");
        assertThat(r.ubicacion()).isEqualTo("aula 204");
        assertThat(r.palabras()).contains("llave");
    }

    @Test
    @DisplayName("Reconoce saludo, agradecimiento, vacío, ayuda y publicación")
    void reconoceIntenciones() {
        assertThat(motor.interpretar("hola", categorias).intencion()).isEqualTo("saludo");
        assertThat(motor.interpretar("gracias", categorias).intencion()).isEqualTo("gracias");
        assertThat(motor.interpretar("de la", categorias).intencion()).isEqualTo("vacio");
        assertThat(motor.interpretar("¿Cómo publico un objeto?", categorias).intencion())
                .isEqualTo("publicar");
        assertThat(motor.interpretar("¿Qué puedes hacer?", categorias).intencion())
                .isEqualTo("ayuda");
    }

    @Test
    @DisplayName("La respuesta válida de IA se normaliza y usa el catálogo real")
    void validaRespuestaCorrecta() {
        IaInterpretacion cruda = new IaInterpretacion(
                "BUSCAR", "encontrado", 2L, "Biblioteca Central",
                List.of("Mochilas", "negra"), List.of("Negro"));

        Optional<InterpretacionAsistente> r = validador.validar(cruda, categorias);

        assertThat(r).isPresent();
        assertThat(r.get().intencion()).isEqualTo("buscar");
        assertThat(r.get().tipoBuscado()).isEqualTo("ENCONTRADO");
        assertThat(r.get().categoriaId()).isEqualTo(2L);
        assertThat(r.get().ubicacion()).isEqualTo("biblioteca central");
        assertThat(r.get().palabras()).contains("mochila", "negra", "negro");
    }

    @Test
    @DisplayName("Descarta intención, tipo, categoría o tamaños fuera del contrato")
    void descartaRespuestasInvalidas() {
        assertThat(validador.validar(null, categorias)).isEmpty();
        assertThat(validador.validar(new IaInterpretacion(
                "inventada", "", null, "", List.of(), List.of()), categorias)).isEmpty();
        assertThat(validador.validar(new IaInterpretacion(
                "buscar", "OTRO", null, "", List.of(), List.of()), categorias)).isEmpty();
        assertThat(validador.validar(new IaInterpretacion(
                "buscar", "", null, "x".repeat(81), List.of(), List.of()), categorias)).isEmpty();
        assertThat(validador.validar(new IaInterpretacion(
                "buscar", "", null, "",
                java.util.stream.IntStream.range(0, 13)
                        .mapToObj(i -> "palabra" + i).toList(), List.of()),
                categorias)).isEmpty();

        Optional<InterpretacionAsistente> categoriaInexistente = validador.validar(
                new IaInterpretacion("buscar", "", 99L, "", List.of("mochila"), List.of()),
                categorias);
        assertThat(categoriaInexistente).isPresent();
        assertThat(categoriaInexistente.get().categoriaId()).isNull();

        Optional<InterpretacionAsistente> vacia = validador.validar(
                new IaInterpretacion("buscar", "", null, "", List.of(), List.of()), categorias);
        assertThat(vacia).isPresent();
        assertThat(vacia.get().intencion()).isEqualTo("vacio");
    }

    @Test
    @DisplayName("La IA no puede usar una categoría desactivada")
    void noUsaCategoriaDesactivada() {
        Optional<InterpretacionAsistente> r = validador.validar(
                new IaInterpretacion("buscar", "", 4L, "", List.of("mochila"), List.of()),
                categorias);
        assertThat(r).isPresent();
        assertThat(r.get().categoriaId()).isNull();
    }
}
