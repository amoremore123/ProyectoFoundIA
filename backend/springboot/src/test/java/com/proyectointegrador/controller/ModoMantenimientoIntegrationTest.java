package com.proyectointegrador.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.proyectointegrador.config.ModoMantenimientoPrueba;
import com.proyectointegrador.entity.Rol;
import com.proyectointegrador.entity.Usuario;
import com.proyectointegrador.repository.UsuarioRepository;
import com.proyectointegrador.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// H70: modo mantenimiento mientras el panel restaura una versión (la tabla la crea Django en MySQL; aquí, para H2)
@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:mantenimiento_h70;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.main.banner-mode=off", "logging.level.root=WARN"})
@AutoConfigureMockMvc
class ModoMantenimientoIntegrationTest {

    @Autowired MockMvc api;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper json;
    @Autowired UsuarioRepository usuarios;

    Usuario ana;

    @BeforeEach
    void preparar() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS restauraciones (id BIGINT AUTO_INCREMENT PRIMARY KEY, "
                + "version VARCHAR(20) NOT NULL, modo VARCHAR(10) NOT NULL, estado VARCHAR(12) NOT NULL)");
        jdbc.update("DELETE FROM restauraciones");
        // Toda esta clase prueba el mantenimiento encendido: hay una restauración solicitada desde el panel
        jdbc.update("INSERT INTO restauraciones (version, modo, estado) VALUES ('20261124T014717Z', 'APLICADA', 'SOLICITADA')");
        Usuario u = new Usuario();
        u.setNombre("Ana");
        u.setApellido("QA");
        u.setCorreo(UUID.randomUUID() + "@prueba.invalid");
        u.setPassword("no-se-usa");
        u.setVerificado(true);
        u.setRol(Rol.USUARIO);
        ana = usuarios.saveAndFlush(u);
    }

    org.springframework.test.web.servlet.request.RequestPostProcessor comoAna() {
        var p = UserPrincipal.from(ana);
        return authentication(new UsernamePasswordAuthenticationToken(p, null, p.getAuthorities()));
    }

    @Test
    @DisplayName("Con una restauración en curso, las escrituras responden 503 con el mensaje y Retry-After")
    void escriturasEn503() throws Exception {
        api.perform(post("/api/campanas/visitas").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"origen\":\"semana-h70\",\"visitaId\":\"visita-0001\"}"))
                .andExpect(status().isServiceUnavailable())
                .andExpect(header().string("Retry-After", "60"))
                .andExpect(jsonPath("$.status").value(503))
                .andExpect(jsonPath("$.mensaje").value(containsString("restaurando una versión anterior")));
        api.perform(put("/api/notificaciones/preferencias").with(comoAna()).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"coincidencias\":true,\"contactos\":true,\"reclamos\":true}"))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    @DisplayName("Las lecturas y el inicio de sesión siguen funcionando")
    void lecturasSiguen() throws Exception {
        api.perform(get("/api/categorias")).andExpect(status().isOk());
        api.perform(get("/api/notificaciones/preferencias").with(comoAna())).andExpect(status().isOk());
        api.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\":\"nadie@prueba.invalid\",\"password\":\"Segura123!\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Se apaga al terminar; si la base no responde justo después del mantenimiento, sigue en 503")
    void transiciones() {
        AtomicLong reloj = new AtomicLong(1_000_000);
        var filtro = ModoMantenimientoPrueba.nuevo(jdbc, json, reloj::get);
        assertThat(ModoMantenimientoPrueba.hay(filtro)).isTrue();
        jdbc.update("UPDATE restauraciones SET estado = 'COMPLETADA'");
        assertThat(ModoMantenimientoPrueba.hay(filtro)).isTrue();   // dentro de los 2 s de caché
        reloj.addAndGet(2_500);
        assertThat(ModoMantenimientoPrueba.hay(filtro)).isFalse();

        // La restauración reemplaza la base: la tabla no está unos segundos
        jdbc.update("UPDATE restauraciones SET estado = 'EN_PROCESO'");
        reloj.addAndGet(2_500);
        assertThat(ModoMantenimientoPrueba.hay(filtro)).isTrue();
        jdbc.execute("DROP TABLE restauraciones");
        reloj.addAndGet(2_500);
        assertThat(ModoMantenimientoPrueba.hay(filtro)).isTrue();   // hubo mantenimiento hace poco
        reloj.addAndGet(200_000);
        assertThat(ModoMantenimientoPrueba.hay(filtro)).isFalse();  // base sin la tabla: no hay restauraciones
    }

    private static org.hamcrest.Matcher<String> containsString(String texto) {
        return org.hamcrest.Matchers.containsString(texto);
    }
}
