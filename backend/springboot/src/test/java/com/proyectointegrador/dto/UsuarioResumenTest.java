package com.proyectointegrador.dto;

import com.proyectointegrador.entity.Usuario;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("H19 - Vista pública del usuario")
class UsuarioResumenTest {

    private Usuario usuario(String apellido) {
        Usuario u = new Usuario();
        u.setId(7L);
        u.setNombre("Ana");
        u.setApellido(apellido);
        u.setCorreo("ana@prueba.invalid");
        return u;
    }

    @Test
    @DisplayName("Solo expone id, nombre e inicial del apellido")
    void soloDatosPublicos() {
        UsuarioResumen publico = UsuarioResumen.publico(usuario("Pérez"));
        assertThat(publico.id()).isEqualTo(7L);
        assertThat(publico.nombre()).isEqualTo("Ana");
        assertThat(publico.apellido()).isEqualTo("P.");
        assertThat(publico.toString()).doesNotContain("ana@prueba.invalid", "Pérez");
    }

    @Test
    @DisplayName("Inicial con tildes, ñ, espacios y apellidos vacíos")
    void inicialDelApellido() {
        assertThat(UsuarioResumen.inicial("  ñandú")).isEqualTo("Ñ.");
        assertThat(UsuarioResumen.inicial("Álvarez")).isEqualTo("Á.");
        assertThat(UsuarioResumen.inicial("de la Cruz")).isEqualTo("D.");
        assertThat(UsuarioResumen.inicial("")).isEmpty();
        assertThat(UsuarioResumen.inicial(null)).isEmpty();
    }

    @Test
    @DisplayName("Sin usuario no inventa datos")
    void usuarioNulo() {
        assertThat(UsuarioResumen.publico(null)).isNull();
    }
}
