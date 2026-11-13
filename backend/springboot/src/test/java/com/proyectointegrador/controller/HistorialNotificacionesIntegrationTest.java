package com.proyectointegrador.controller;

import com.proyectointegrador.service.NotificacionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// H25: historial de avisos con filtro leídas/no leídas, paginación y «marcar todas» en la cuenta propia
@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:historial_h25;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.main.banner-mode=off", "logging.level.root=WARN"})
@AutoConfigureMockMvc
class HistorialNotificacionesIntegrationTest extends Sprint2Fixtures {

    static final String RUTA = "/api/notificaciones/historial";
    @Autowired NotificacionService notificar;

    private void avisosDeAna(int cantidad) {
        for (long i = 1; i <= cantidad; i++) notificar.notificarReclamo(ana, i);
    }

    @Test
    void sinSesionResponde401() throws Exception {
        api.perform(get(RUTA)).andExpect(status().isUnauthorized());
        api.perform(put("/api/notificaciones/leidas")).andExpect(status().isUnauthorized());
    }

    @Test
    void filtraPorLecturaSinCambiarLosAvisos() throws Exception {
        avisosDeAna(3);
        var primero = notificaciones.findAll().get(0);
        api.perform(put("/api/notificaciones/" + primero.getId() + "/leida").with(como(ana))).andExpect(status().isOk());

        api.perform(get(RUTA).param("estado", "NO_LEIDAS").with(como(ana))).andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido", hasSize(2)))
                .andExpect(jsonPath("$.contenido[0].leida").value(false))
                .andExpect(jsonPath("$.noLeidas").value(2))
                .andExpect(jsonPath("$.estado").value("NO_LEIDAS"));
        api.perform(get(RUTA).param("estado", "LEIDAS").with(como(ana)))
                .andExpect(jsonPath("$.contenido", hasSize(1)))
                .andExpect(jsonPath("$.contenido[0].id").value(primero.getId()));
        api.perform(get(RUTA).with(como(ana))).andExpect(jsonPath("$.total").value(3));

        // Consultar el historial no marca nada como leído
        assertThat(notificaciones.findAll().stream().filter(n -> !n.getLeida()).count()).isEqualTo(2);
    }

    @Test
    void paginaDelMasRecienteAlMasAntiguo() throws Exception {
        avisosDeAna(5);
        api.perform(get(RUTA).param("tamano", "2").with(como(ana))).andExpect(status().isOk())
                .andExpect(jsonPath("$.contenido", hasSize(2)))
                .andExpect(jsonPath("$.total").value(5))
                .andExpect(jsonPath("$.totalPaginas").value(3))
                .andExpect(jsonPath("$.contenido[0].mensaje").value(org.hamcrest.Matchers.containsString("#5")));
        api.perform(get(RUTA).param("tamano", "2").param("pagina", "2").with(como(ana)))
                .andExpect(jsonPath("$.contenido", hasSize(1)))
                .andExpect(jsonPath("$.pagina").value(2));
    }

    @Test
    void rechazaFiltroOPaginacionInvalidos() throws Exception {
        api.perform(get(RUTA).param("estado", "BORRADAS").with(como(ana))).andExpect(status().isBadRequest());
        api.perform(get(RUTA).param("pagina", "-1").with(como(ana))).andExpect(status().isBadRequest());
        api.perform(get(RUTA).param("tamano", "0").with(como(ana))).andExpect(status().isBadRequest());
        api.perform(get(RUTA).param("tamano", "51").with(como(ana))).andExpect(status().isBadRequest());
    }

    @Test
    void soloVeSusPropiosAvisos() throws Exception {
        avisosDeAna(2);
        notificar.notificarReclamo(luis, 9L);
        api.perform(get(RUTA).with(como(luis)))
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.noLeidas").value(1));
    }

    @Test
    void marcarTodasSoloAfectaLaCuentaPropiaYEsRepetible() throws Exception {
        avisosDeAna(3);
        notificar.notificarReclamo(luis, 9L);

        api.perform(put("/api/notificaciones/leidas").with(como(ana))).andExpect(status().isOk())
                .andExpect(jsonPath("$.actualizadas").value(3))
                .andExpect(jsonPath("$.noLeidas").value(0));
        api.perform(put("/api/notificaciones/leidas").with(como(ana)))
                .andExpect(jsonPath("$.actualizadas").value(0));

        var deLuis = notificaciones.findAll().stream().filter(n -> n.getUsuario().getId().equals(luis.getId())).toList();
        assertThat(deLuis).hasSize(1);
        assertThat(deLuis.get(0).getLeida()).isFalse();
        api.perform(get(RUTA).param("estado", "NO_LEIDAS").with(como(ana))).andExpect(jsonPath("$.contenido", hasSize(0)));
    }
}
