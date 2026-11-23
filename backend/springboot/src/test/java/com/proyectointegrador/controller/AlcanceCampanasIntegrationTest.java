package com.proyectointegrador.controller;

import com.proyectointegrador.exception.EnvioCorreoException;
import com.proyectointegrador.repository.UsuarioRepository;
import com.proyectointegrador.service.AlcanceCampanaService;
import com.proyectointegrador.service.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// H67: contar visitas y registros por origen de campaña. Las tablas las crea Django en MySQL; aquí, para H2.
@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:alcance_h67;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.main.banner-mode=off", "logging.level.root=WARN"})
@AutoConfigureMockMvc
class AlcanceCampanasIntegrationTest {

    static final String RUTA = "/api/campanas/visitas";

    @Autowired MockMvc api;
    @Autowired JdbcTemplate jdbc;
    @Autowired UsuarioRepository usuarios;
    @MockitoBean EmailService email;

    long semana;
    long finalizada;
    int ip = 1;

    @BeforeEach
    void preparar() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS campanas (id BIGINT AUTO_INCREMENT PRIMARY KEY, origen VARCHAR(40) NOT NULL UNIQUE, "
                + "destino VARCHAR(255) NOT NULL DEFAULT '/', finalizada_en DATETIME(6) NULL)");
        jdbc.execute("CREATE TABLE IF NOT EXISTS alcance_campanas (id BIGINT AUTO_INCREMENT PRIMARY KEY, campana_id BIGINT NOT NULL, "
                + "tipo VARCHAR(10) NOT NULL, visita_id VARCHAR(64) NULL, fecha DATETIME(6) NOT NULL, "
                + "CONSTRAINT uq_alcance_visita UNIQUE (campana_id, visita_id))");
        jdbc.update("DELETE FROM alcance_campanas");
        jdbc.update("DELETE FROM campanas");
        usuarios.deleteAll();
        jdbc.update("INSERT INTO campanas (origen) VALUES ('semana-perdidos-2026')");
        semana = jdbc.queryForObject("SELECT id FROM campanas WHERE origen = 'semana-perdidos-2026'", Long.class);
        jdbc.update("INSERT INTO campanas (origen, finalizada_en) VALUES ('feria-2025', CURRENT_TIMESTAMP)");
        finalizada = jdbc.queryForObject("SELECT id FROM campanas WHERE origen = 'feria-2025'", Long.class);
    }

    /** Cada prueba usa otra IP para no chocar con el límite por minuto de las demás. */
    ResultActions visitar(String origen, String visitaId) throws Exception {
        return visitarDesde("10.0.0." + ip, origen, visitaId);
    }

    ResultActions visitarDesde(String direccion, String origen, String visitaId) throws Exception {
        return api.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON)
                .with(r -> { r.setRemoteAddr(direccion); return r; })
                .content("{\"origen\":\"" + origen + "\",\"visitaId\":\"" + visitaId + "\"}"));
    }

    int contar(long campana, String tipo) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM alcance_campanas WHERE campana_id = ? AND tipo = ?",
                Integer.class, campana, tipo);
    }

    ResultActions registrar(String correo, String origenJson) throws Exception {
        return api.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(
                "{\"nombre\":\"Rosa\",\"apellido\":\"QA\",\"correo\":\"" + correo + "\",\"password\":\"Segura123!\","
                        + "\"aceptaTerminos\":true,\"versionLegal\":\"" + versionLegal() + "\""
                        + (origenJson == null ? "" : ",\"origen\":" + origenJson) + "}"));
    }

    String versionLegal() {
        return com.proyectointegrador.service.DocumentosLegales.VERSION;
    }

    @Test
    @DisplayName("Una visita suma una vez: el mismo visitaId no vuelve a sumar, otro sí")
    void visitaSumaUnaVez() throws Exception {
        ip = 11;
        visitar("semana-perdidos-2026", "6f1c2b9e-4a7d-4f1e-9c3b-2d5e8a7b1c40").andExpect(status().isNoContent());
        visitar("semana-perdidos-2026", "6f1c2b9e-4a7d-4f1e-9c3b-2d5e8a7b1c40").andExpect(status().isNoContent());
        assertThat(contar(semana, "VISITA")).isEqualTo(1);
        visitar("semana-perdidos-2026", "otra-visita-123").andExpect(status().isNoContent());
        assertThat(contar(semana, "VISITA")).isEqualTo(2);
    }

    @Test
    @DisplayName("Un origen inexistente responde 204 sin contar; una campaña finalizada sí cuenta")
    void origenInexistenteYFinalizada() throws Exception {
        ip = 12;
        visitar("no-existe", "visita-000001").andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM alcance_campanas", Integer.class)).isZero();
        visitar("feria-2025", "visita-000002").andExpect(status().isNoContent());
        assertThat(contar(finalizada, "VISITA")).isEqualTo(1);
    }

    @Test
    @DisplayName("Formato inválido 400, sin contar")
    void formatoInvalido() throws Exception {
        ip = 13;
        for (String[] caso : new String[][]{{"Semana", "visita-0001"}, {"ab", "visita-0001"}, {"semana--x", "visita-0001"},
                {"semana-perdidos-2026", "corto"}, {"semana-perdidos-2026", "con espacios no"}}) {
            visitar(caso[0], caso[1]).andExpect(status().isBadRequest());
        }
        api.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON).content("{\"origen\":\"semana-perdidos-2026\"}"))
                .andExpect(status().isBadRequest());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM alcance_campanas", Integer.class)).isZero();
    }

    @Test
    @DisplayName("Más de 30 visitas por minuto desde el mismo equipo responde 429; otro equipo sigue")
    void limitePorMinuto() throws Exception {
        for (int i = 0; i < AlcanceCampanaService.VISITAS_POR_MINUTO; i++) {
            visitarDesde("10.0.1.1", "semana-perdidos-2026", "visita-limite-" + i).andExpect(status().isNoContent());
        }
        visitarDesde("10.0.1.1", "semana-perdidos-2026", "visita-limite-x").andExpect(status().isTooManyRequests());
        visitarDesde("10.0.1.2", "semana-perdidos-2026", "visita-limite-y").andExpect(status().isNoContent());
        assertThat(contar(semana, "VISITA")).isEqualTo(AlcanceCampanaService.VISITAS_POR_MINUTO + 1);
    }

    @Test
    @DisplayName("No se guarda la IP ni nada de la persona")
    void sinDatosPersonales() throws Exception {
        List<String> columnas = jdbc.queryForList(
                "SELECT LOWER(column_name) FROM information_schema.columns WHERE LOWER(table_name) = 'alcance_campanas'",
                String.class);
        assertThat(columnas).containsExactlyInAnyOrder("id", "campana_id", "tipo", "visita_id", "fecha");
    }

    @Test
    @DisplayName("Registrarse con un origen válido suma un registro, sin guardar qué cuenta fue")
    void registroConOrigen() throws Exception {
        registrar("rosa-h67@prueba.invalid", "\"semana-perdidos-2026\"").andExpect(status().isCreated());
        assertThat(contar(semana, "REGISTRO")).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT visita_id FROM alcance_campanas WHERE tipo = 'REGISTRO'", String.class)).isNull();
    }

    @Test
    @DisplayName("Sin origen, con uno inexistente o mal escrito, el registro funciona y no suma")
    void registroSinOrigenValido() throws Exception {
        registrar("sin-origen-h67@prueba.invalid", null).andExpect(status().isCreated());
        registrar("inexistente-h67@prueba.invalid", "\"no-existe\"").andExpect(status().isCreated());
        registrar("mal-h67@prueba.invalid", "\"Origen Malo!\"").andExpect(status().isCreated());
        registrar("nulo-h67@prueba.invalid", "null").andExpect(status().isCreated());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM alcance_campanas", Integer.class)).isZero();
        assertThat(usuarios.count()).isEqualTo(4);
    }

    @Test
    @DisplayName("Si el registro falla, el registro de la campaña tampoco se cuenta")
    void registroFallidoNoSuma() throws Exception {
        doThrow(new EnvioCorreoException("caído")).when(email)
                .enviarCodigoVerificacion(anyString(), anyString(), anyString(), anyLong());
        registrar("falla-h67@prueba.invalid", "\"semana-perdidos-2026\"").andExpect(status().isServiceUnavailable());
        assertThat(contar(semana, "REGISTRO")).isZero();
        assertThat(usuarios.count()).isZero();
    }
}
