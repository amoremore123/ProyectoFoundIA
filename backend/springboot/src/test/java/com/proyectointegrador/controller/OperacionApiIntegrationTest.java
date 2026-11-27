package com.proyectointegrador.controller;

import com.proyectointegrador.config.CacheConfig;
import com.proyectointegrador.entity.Categoria;
import com.proyectointegrador.repository.CategoriaRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Swagger lo cubren SwaggerIntegrationTest (encendido) y SwaggerApagadoIntegrationTest (apagado)
@DisplayName("Actuator y caché de categorías")
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:foundia_operacion_api;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.main.banner-mode=off",
        "logging.level.root=WARN",
        // Maven apaga la caché en las pruebas; aquí se enciende para comprobarla
        "spring.cache.type=caffeine"
})
@AutoConfigureMockMvc
class OperacionApiIntegrationTest {

    @Autowired private MockMvc api;
    @Autowired private CacheManager caches;
    @Autowired private CategoriaRepository categorias;

    @Test
    void healthEsPublicoYElRestoDeActuatorNo() throws Exception {
        api.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components.db.status").value("UP"))
                // Sin detalles internos (nombre de la base, rutas del disco)
                .andExpect(jsonPath("$.components.db.details").doesNotExist());
        api.perform(get("/actuator/env")).andExpect(result ->
                assertThat(result.getResponse().getStatus()).isIn(401, 403, 404));
        api.perform(get("/actuator/beans")).andExpect(result ->
                assertThat(result.getResponse().getStatus()).isIn(401, 403, 404));
    }

    @Test
    void lasCategoriasSeGuardanEnCache() throws Exception {
        var cache = caches.getCache(CacheConfig.CATEGORIAS);
        assertThat(cache).isNotNull();
        cache.clear();
        Categoria c = new Categoria();
        c.setNombre("Cache " + System.nanoTime());
        c.setEstado(true);
        categorias.saveAndFlush(c);

        api.perform(get("/api/categorias")).andExpect(status().isOk());
        assertThat(cache.get(org.springframework.cache.interceptor.SimpleKey.EMPTY)).isNotNull();
        cache.clear();
    }
}
