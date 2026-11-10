package com.proyectointegrador.controller;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// H49: el endpoint pide sesión, no guarda nada y sin clave de IA responde con el motor local
@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:sugerencia_h49;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.main.banner-mode=off", "logging.level.root=WARN", "app.ia.api-key="})
@AutoConfigureMockMvc
class SugerenciaPublicacionIntegrationTest extends Sprint2Fixtures {

    static final String RUTA = "/api/asistente/sugerir-publicacion";

    @Test
    void sinSesionResponde401() throws Exception {
        api.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\"mochila\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void sugiereSinGuardarNada() throws Exception {
        long objetosAntes = jdbc.queryForObject("SELECT COUNT(*) FROM objetos", Long.class);
        api.perform(post(RUTA).with(como(ana)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"mochila\",\"descripcion\":\"mochila negra con llavero\",\"tipo\":\"PERDIDO\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.origen").value("LOCAL"))
                .andExpect(jsonPath("$.descripcion").value("Mochila negra con llavero."))
                .andExpect(jsonPath("$.aviso").exists());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM objetos", Long.class)).isEqualTo(objetosAntes);
    }

    @Test
    void rechazaTextoVacioOMuyLargo() throws Exception {
        api.perform(post(RUTA).with(como(ana)).contentType(MediaType.APPLICATION_JSON).content("{\"nombre\":\"\",\"descripcion\":\" \"}"))
                .andExpect(status().isBadRequest());
        api.perform(post(RUTA).with(como(ana)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"descripcion\":\"" + "a".repeat(1001) + "\"}"))
                .andExpect(status().isBadRequest());
    }
}
