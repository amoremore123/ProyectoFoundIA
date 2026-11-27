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

// Swagger/OpenAPI encendido (SWAGGER_HABILITADO=true)
@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:swagger_si;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa",
        "spring.datasource.password=", "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect", "spring.main.banner-mode=off",
        "logging.level.root=WARN", "springdoc.api-docs.enabled=true", "springdoc.swagger-ui.enabled=true"})
@AutoConfigureMockMvc
class SwaggerIntegrationTest {
    @Autowired MockMvc api;

    @Test
    @DisplayName("Publica el OpenAPI sin sesión, con las rutas de la API y el botón de token JWT")
    void publicaElContrato() throws Exception {
        api.perform(get("/v3/api-docs")).andExpect(status().isOk())
                .andExpect(jsonPath("$.info.title").value(containsString("FoundIA")))
                .andExpect(jsonPath("$.paths['/api/auth/login']").exists())
                .andExpect(jsonPath("$.paths['/api/conversaciones/{id}/mensajes']").exists())
                .andExpect(jsonPath("$.paths['/api/avisos-destacados']").exists())
                .andExpect(jsonPath("$.components.securitySchemes.jwt.scheme").value("bearer"));
    }

    @Test
    @DisplayName("La pantalla de Swagger UI abre sin sesión")
    void pantalla() throws Exception {
        api.perform(get("/swagger-ui.html")).andExpect(status().is3xxRedirection());
        api.perform(get("/swagger-ui/index.html")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Abrir Swagger no abre la API: las rutas protegidas siguen pidiendo sesión")
    void noAbreLaApi() throws Exception {
        api.perform(get("/api/perfil")).andExpect(status().isUnauthorized());
    }
}
