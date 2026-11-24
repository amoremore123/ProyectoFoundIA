package com.proyectointegrador.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Las fotos de seed.sql se ven aunque no estén en el volumen de subidas")
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:foundia_fotos_semilla;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.main.banner-mode=off",
        "logging.level.root=WARN",
        "app.upload.directory=target/test-uploads-vacio"
})
@AutoConfigureMockMvc
class FotosSemillaIntegrationTest {

    @Autowired private MockMvc api;

    @Test
    void lasTresFotosDelSeedResponden() throws Exception {
        for (String foto : new String[]{"mochila-negra.jpg", "celular-samsung.jpg", "llavero.jpg"}) {
            api.perform(get("/uploads/objetos/" + foto))
                    .andExpect(status().isOk())
                    .andExpect(content().contentType("image/jpeg"));
        }
    }

    @Test
    void loQueNoExisteSigueDando404YLosPerfilesSiguenPrivados() throws Exception {
        api.perform(get("/uploads/objetos/no-existe.jpg")).andExpect(status().isNotFound());
        api.perform(get("/uploads/CREDITOS.md")).andExpect(status().isNotFound());
        api.perform(get("/uploads/perfiles/00000000-0000-0000-0000-000000000000.png"))
                .andExpect(result -> org.assertj.core.api.Assertions.assertThat(result.getResponse().getStatus()).isIn(401, 403));
    }
}
