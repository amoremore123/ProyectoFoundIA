package com.proyectointegrador.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;

import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Un método o un tipo de contenido que la ruta no acepta es un error del cliente (405 y 415), no un 500.
@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:errores_cliente;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.main.banner-mode=off", "logging.level.root=WARN"})
@AutoConfigureMockMvc
class ErroresDelClienteIntegrationTest extends Sprint2Fixtures {

    static final String PREFERENCIAS = "/api/notificaciones/preferencias";

    @Test
    @DisplayName("Un método no permitido responde 405 con los métodos admitidos en Allow")
    void metodoNoPermitido() throws Exception {
        api.perform(post(PREFERENCIAS).with(como(ana)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"coincidencias\":true,\"contactos\":true,\"reclamos\":true}"))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(header().string("Allow", allOf(containsString("GET"), containsString("PUT"))))
                .andExpect(jsonPath("$.status").value(405))
                .andExpect(jsonPath("$.mensaje").value("El método POST no está permitido en esta ruta"))
                .andExpect(jsonPath("$.path").value(PREFERENCIAS));
        api.perform(delete(PREFERENCIAS).with(como(ana))).andExpect(status().isMethodNotAllowed());
    }

    @Test
    @DisplayName("Un tipo de contenido no admitido responde 415")
    void tipoDeContenidoNoAdmitido() throws Exception {
        api.perform(put(PREFERENCIAS).with(como(ana)).contentType(MediaType.TEXT_PLAIN).content("coincidencias=true"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(jsonPath("$.status").value(415))
                .andExpect(jsonPath("$.mensaje").value("Tipo de contenido no admitido en esta ruta"));
    }

    @Test
    @DisplayName("Sin sesión sigue respondiendo 401 antes de mirar el método")
    void sinSesionSigueSiendo401() throws Exception {
        api.perform(post(PREFERENCIAS).contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isUnauthorized());
    }
}
