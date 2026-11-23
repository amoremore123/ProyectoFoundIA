package com.proyectointegrador.controller;

import com.proyectointegrador.service.AvisoDestacadoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.sql.Date;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.hasKey;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// H71: API pública de avisos destacados vigentes. Las tablas las crea Django en MySQL; aquí se crean para H2.
@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:avisos_h71;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.main.banner-mode=off", "logging.level.root=WARN"})
@AutoConfigureMockMvc
class AvisosDestacadosIntegrationTest {

    static final String RUTA = "/api/avisos-destacados";

    @Autowired MockMvc api;
    @Autowired JdbcTemplate jdbc;

    LocalDate hoy;

    @BeforeEach
    void preparar() {
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS campanas (id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    origen VARCHAR(40) NOT NULL, destino VARCHAR(255) NOT NULL DEFAULT '/')""");
        jdbc.execute("""
                CREATE TABLE IF NOT EXISTS avisos_destacados (id BIGINT AUTO_INCREMENT PRIMARY KEY,
                    titulo VARCHAR(80) NOT NULL, texto VARCHAR(300) NOT NULL, destino VARCHAR(255) NULL,
                    campana_id BIGINT NULL, fecha_inicio DATE NOT NULL, fecha_fin DATE NOT NULL,
                    retirado_en DATETIME(6) NULL, creado_por_id BIGINT NOT NULL,
                    fecha_creacion DATETIME(6) NOT NULL, fecha_actualizacion DATETIME(6) NOT NULL)""");
        jdbc.update("DELETE FROM avisos_destacados");
        jdbc.update("DELETE FROM campanas");
        hoy = LocalDate.now(ZoneOffset.ofHours(-5));
    }

    long aviso(String titulo, int inicio, int fin, String destino, Long campana, boolean retirado) {
        Timestamp ahora = Timestamp.valueOf(LocalDateTime.now(ZoneOffset.UTC));
        jdbc.update("""
                INSERT INTO avisos_destacados (titulo, texto, destino, campana_id, fecha_inicio, fecha_fin,
                    retirado_en, creado_por_id, fecha_creacion, fecha_actualizacion)
                VALUES (?, 'Texto del aviso', ?, ?, ?, ?, ?, 1, ?, ?)""",
                titulo, destino, campana, Date.valueOf(hoy.plusDays(inicio)), Date.valueOf(hoy.plusDays(fin)),
                retirado ? ahora : null, ahora, ahora);
        return jdbc.queryForObject("SELECT MAX(id) FROM avisos_destacados", Long.class);
    }

    long aviso(String titulo, int inicio, int fin) { return aviso(titulo, inicio, fin, "/explorar", null, false); }

    @Test
    @DisplayName("Sin sesión devuelve solo los vigentes; programados, vencidos y retirados no")
    void soloVigentes() throws Exception {
        aviso("Programado", 1, 5);
        aviso("Vencido", -6, -1);
        aviso("Retirado", -1, 3, "/explorar", null, true);
        long ultimoDia = aviso("Termina hoy", -4, 0);
        long empiezaHoy = aviso("Empieza hoy", 0, 3);
        api.perform(get(RUTA)).andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].id").value(empiezaHoy))
                .andExpect(jsonPath("$[1].id").value(ultimoDia))
                .andExpect(jsonPath("$[1].fechaFin").value(hoy.toString()))
                .andExpect(jsonPath("$[0].titulo").value("Empieza hoy"))
                .andExpect(jsonPath("$[0].texto").value("Texto del aviso"))
                .andExpect(jsonPath("$[0].enlace").value("/explorar"))
                // Sin datos de quién lo creó ni del estado interno
                .andExpect(jsonPath("$[0]", not(hasKey("creadoPor"))))
                .andExpect(jsonPath("$[0]", not(hasKey("retiradoEn"))));
    }

    @Test
    @DisplayName("Como máximo 5, el que empezó más recientemente primero")
    void maximoYOrden() throws Exception {
        for (int i = 0; i < 7; i++) aviso("Aviso " + i, -i, 2);
        api.perform(get(RUTA)).andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(AvisoDestacadoService.MAXIMO)))
                .andExpect(jsonPath("$[0].titulo").value("Aviso 0"))
                .andExpect(jsonPath("$[4].titulo").value("Aviso 4"));
    }

    @Test
    @DisplayName("Con campaña abre su enlace con ?origen= para que H67 cuente la visita")
    void enlaceDeCampana() throws Exception {
        jdbc.update("INSERT INTO campanas (origen, destino) VALUES ('semana-perdidos-2026', '/explorar')");
        Long campana = jdbc.queryForObject("SELECT MAX(id) FROM campanas", Long.class);
        aviso("Con campaña", 0, 1, null, campana, false);
        aviso("Sin enlace", -1, 1, null, null, false);
        api.perform(get(RUTA)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].enlace").value("/explorar?origen=semana-perdidos-2026"))
                .andExpect(jsonPath("$[1].enlace").doesNotExist());
    }

    @Test
    @DisplayName("Sin avisos vigentes devuelve una lista vacía")
    void vacio() throws Exception {
        aviso("Vencido", -3, -1);
        api.perform(get(RUTA)).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
    }
}
