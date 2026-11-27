package com.proyectointegrador.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Swagger/OpenAPI apagado por defecto: no se publica nada
@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:swagger_no;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa",
        "spring.datasource.password=", "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect", "spring.main.banner-mode=off",
        "logging.level.root=WARN"})
@AutoConfigureMockMvc
class SwaggerApagadoIntegrationTest {
    @Autowired MockMvc api;

    @Test
    @DisplayName("Sin SWAGGER_HABILITADO no se publica nada (404)")
    void noPublica() throws Exception {
        api.perform(get("/v3/api-docs")).andExpect(status().isNotFound());
        api.perform(get("/swagger-ui/index.html")).andExpect(status().isNotFound());
    }
}
