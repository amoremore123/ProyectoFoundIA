package com.proyectointegrador.security;

import com.proyectointegrador.entity.EstadoUsuario;
import com.proyectointegrador.entity.Usuario;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("H12 - Estado de la cuenta en el principal de seguridad")
class UserPrincipalTest {

    private Usuario usuario(EstadoUsuario estado, boolean verificado) {
        Usuario usuario = new Usuario();
        usuario.setId(1L);
        usuario.setNombre("Ana");
        usuario.setCorreo("ana@prueba.invalid");
        usuario.setPassword("hash");
        usuario.setEstado(estado);
        usuario.setVerificado(verificado);
        return usuario;
    }

    @Test
    @DisplayName("Una cuenta activa y verificada está habilitada")
    void cuentaActivaVerificada() {
        assertThat(UserPrincipal.from(usuario(EstadoUsuario.ACTIVO, true)).isEnabled()).isTrue();
    }

    @Test
    @DisplayName("Una cuenta suspendida está deshabilitada aunque esté verificada")
    void cuentaSuspendidaDeshabilitada() {
        assertThat(UserPrincipal.from(usuario(EstadoUsuario.SUSPENDIDO, true)).isEnabled()).isFalse();
    }

    @Test
    @DisplayName("Una cuenta sin verificar está deshabilitada")
    void cuentaNoVerificadaDeshabilitada() {
        assertThat(UserPrincipal.from(usuario(EstadoUsuario.ACTIVO, false)).isEnabled()).isFalse();
    }
}
