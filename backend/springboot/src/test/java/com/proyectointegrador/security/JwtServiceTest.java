package com.proyectointegrador.security;

import com.proyectointegrador.entity.Rol;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

@DisplayName("H12 - Token JWT")
class JwtServiceTest {

    private static final String SECRETO = "secreto-de-pruebas-con-al-menos-32-caracteres!!";

    private final UserPrincipal ana = new UserPrincipal(1L, "Ana", "ana@foundia.dev", "hash", Rol.USUARIO);
    private final UserPrincipal luis = new UserPrincipal(2L, "Luis", "luis@foundia.dev", "hash", Rol.USUARIO);

    @Test
    @DisplayName("El token generado contiene el correo y es válido para su dueño")
    void tokenValido() {
        JwtService jwt = new JwtService(SECRETO, 60_000);
        String token = jwt.generateToken(ana);

        assertThat(jwt.extractUsername(token)).isEqualTo("ana@foundia.dev");
        assertThat(jwt.isTokenValid(token, ana)).isTrue();
        assertThat(jwt.isTokenValid(token, luis)).isFalse();
    }

    @Test
    @DisplayName("Un token firmado con otro secreto no es válido")
    void otroSecreto() {
        String token = new JwtService(SECRETO, 60_000).generateToken(ana);
        JwtService otro = new JwtService("otro-secreto-distinto-con-mas-de-32-caracteres", 60_000);

        assertThat(otro.isTokenValid(token, ana)).isFalse();
    }

    @Test
    @DisplayName("Un token expirado no es válido")
    void tokenExpirado() {
        JwtService jwt = new JwtService(SECRETO, -1_000);
        String token = jwt.generateToken(ana);

        assertThat(jwt.isTokenValid(token, ana)).isFalse();
    }
}
