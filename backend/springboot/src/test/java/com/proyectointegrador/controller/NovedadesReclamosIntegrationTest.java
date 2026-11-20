package com.proyectointegrador.controller;

import com.proyectointegrador.service.NotificacionService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// H50: novedades de reclamos de la cuenta propia para la consulta periódica de Android
@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:novedades_h50;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.main.banner-mode=off", "logging.level.root=WARN"})
@AutoConfigureMockMvc
class NovedadesReclamosIntegrationTest extends Sprint2Fixtures {

    static final String RUTA = "/api/notificaciones/reclamos/novedades";
    static final String DESDE = "2000-01-01T00:00:00";
    @Autowired NotificacionService notificar;

    // Django crea los avisos RECLAMO al resolver un reclamo (H43); aquí se guardan igual
    private Long avisoReclamo(com.proyectointegrador.entity.Usuario destino, String titulo) {
        var aviso = new com.proyectointegrador.entity.Notificacion();
        aviso.setUsuario(destino);
        aviso.setTitulo(titulo);
        aviso.setMensaje(titulo);
        aviso.setTipo("RECLAMO");
        aviso.setLeida(Boolean.FALSE);
        return notificaciones.save(aviso).getId();
    }

    @Test
    void sinSesionResponde401() throws Exception {
        api.perform(get(RUTA).param("desde", DESDE)).andExpect(status().isUnauthorized());
    }

    @Test
    void parametrosInvalidosResponden400() throws Exception {
        api.perform(get(RUTA).with(como(ana))).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("Falta el parámetro desde"));
        api.perform(get(RUTA).param("desde", "ayer").with(como(ana))).andExpect(status().isBadRequest());
        api.perform(get(RUTA).param("desde", DESDE).param("despuesDe", "-1").with(como(ana)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void soloDevuelveLosAvisosPropiosEnOrdenYConCursor() throws Exception {
        Long primero = avisoReclamo(ana, "Tu reclamo fue aceptado");
        Long segundo = avisoReclamo(ana, "Tu reclamo fue rechazado");
        avisoReclamo(luis, "Aviso de otra cuenta");
        notificar.notificarReclamo(ana, 9L); // otro tipo (RECLAMO_RECIBIDO): no es una novedad de H50

        api.perform(get(RUTA).param("desde", DESDE).with(como(ana))).andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id").value(primero))
                .andExpect(jsonPath("$[1].id").value(segundo))
                .andExpect(jsonPath("$[0].tipo").value("RECLAMO"));
        api.perform(get(RUTA).param("desde", DESDE).param("despuesDe", primero.toString()).with(como(ana)))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(segundo));

        // Consultar no marca nada como leído
        assertThat(notificaciones.findAll().stream().filter(n -> !n.getLeida()).count()).isEqualTo(4);
    }

    @Test
    void conLaPreferenciaDeReclamosApagadaNoDevuelveNada() throws Exception {
        avisoReclamo(ana, "Tu reclamo fue aceptado");
        api.perform(put("/api/notificaciones/preferencias").with(como(ana)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"coincidencias\":true,\"contactos\":true,\"reclamos\":false}")).andExpect(status().isOk());
        api.perform(get(RUTA).param("desde", DESDE).with(como(ana))).andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }
}
