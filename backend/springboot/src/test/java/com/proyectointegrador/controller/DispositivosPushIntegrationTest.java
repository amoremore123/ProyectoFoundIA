package com.proyectointegrador.controller;

import com.jayway.jsonpath.JsonPath;
import com.proyectointegrador.entity.EstadoUsuario;
import com.proyectointegrador.entity.Rol;
import com.proyectointegrador.entity.Usuario;
import com.proyectointegrador.repository.UsuarioRepository;
import com.proyectointegrador.service.DespachadorPush;
import com.proyectointegrador.service.EmailService;
import com.proyectointegrador.service.EnviadorPush;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// H57: dispositivos para push y despachador (Firebase simulado; el envío real se prueba con la cuenta del equipo)
@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:push_h57;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.main.banner-mode=off", "logging.level.root=WARN",
        // El despachador programado no corre durante la prueba: se llama a mano
        "app.push.retraso-inicial-ms=3600000"})
@AutoConfigureMockMvc
class DispositivosPushIntegrationTest {

    static final String RUTA = "/api/dispositivos";
    static final String PASSWORD = "Segura123!";
    static final String TOKEN_A = "fcm-token-ana-AAAAAAAAAAAAAAAAAAAAAAAA:1";

    /** Firebase simulado: guarda lo enviado y responde según el token. */
    static class FirebaseSimulado implements EnviadorPush {
        final List<String> enviados = new ArrayList<>();
        final List<Mensaje> mensajes = new ArrayList<>();
        final Set<String> invalidos = new HashSet<>();
        boolean disponible = true;
        public boolean disponible() { return disponible; }
        public Resultado enviar(String token, Mensaje m) {
            if (invalidos.contains(token)) return Resultado.TOKEN_INVALIDO;
            enviados.add(token);
            mensajes.add(m);
            return Resultado.ENVIADO;
        }
    }

    @TestConfiguration
    static class Configuracion {
        @Bean @Primary FirebaseSimulado firebaseSimulado() { return new FirebaseSimulado(); }
    }

    @Autowired MockMvc api;
    @Autowired UsuarioRepository usuarios;
    @Autowired JdbcTemplate jdbc;
    @Autowired DespachadorPush despachador;
    @Autowired FirebaseSimulado firebase;
    @MockitoBean EmailService email;

    Usuario ana, luis;

    @BeforeEach
    void preparar() {
        jdbc.update("DELETE FROM dispositivos");
        jdbc.update("DELETE FROM notificaciones");
        jdbc.update("DELETE FROM preferencias_notificacion");
        jdbc.update("DELETE FROM tokens_revocados");
        usuarios.deleteAll();
        ana = usuario("Ana", "ana-h57@prueba.invalid");
        luis = usuario("Luis", "luis-h57@prueba.invalid");
        firebase.enviados.clear();
        firebase.mensajes.clear();
        firebase.invalidos.clear();
        firebase.disponible = true;
    }

    Usuario usuario(String nombre, String correo) {
        Usuario u = new Usuario();
        u.setNombre(nombre);
        u.setApellido("QA");
        u.setCorreo(correo);
        u.setPassword(new BCryptPasswordEncoder(4).encode(PASSWORD));
        u.setRol(Rol.USUARIO);
        u.setEstado(EstadoUsuario.ACTIVO);
        u.setVerificado(true);
        return usuarios.saveAndFlush(u);
    }

    String login(Usuario u) throws Exception {
        String r = api.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\":\"" + u.getCorreo() + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(r, "$.token");
    }

    ResultActions registrar(String jwt, String token) throws Exception {
        return api.perform(post(RUTA).header("Authorization", "Bearer " + jwt).contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\",\"plataforma\":\"ANDROID\"}"));
    }

    int dispositivosDe(Usuario u) {
        return jdbc.queryForObject("SELECT COUNT(*) FROM dispositivos WHERE usuario_id = ?", Integer.class, u.getId());
    }

    long aviso(Usuario u, String tipo, LocalDateTime creado) {
        jdbc.update("INSERT INTO notificaciones (usuario_id, titulo, mensaje, tipo, enlace, leida, fecha_creacion) "
                + "VALUES (?, 'Título', 'Cuerpo del aviso', ?, '/objeto/5', FALSE, ?)", u.getId(), tipo, Timestamp.valueOf(creado));
        return jdbc.queryForObject("SELECT MAX(id) FROM notificaciones", Long.class);
    }

    LocalDateTime ahora() { return LocalDateTime.now(ZoneOffset.UTC); }

    @Test
    @DisplayName("Registrar es idempotente, no devuelve el token y exige sesión")
    void registrar() throws Exception {
        String jwt = login(ana);
        api.perform(post(RUTA).contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + TOKEN_A + "\",\"plataforma\":\"ANDROID\"}")).andExpect(status().isUnauthorized());
        Number id = JsonPath.read(registrar(jwt, TOKEN_A).andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(), "$.id");
        registrar(jwt, TOKEN_A).andExpect(status().isOk()).andExpect(jsonPath("$.id").value(id.longValue()));
        assertThat(dispositivosDe(ana)).isEqualTo(1);
        api.perform(get(RUTA).header("Authorization", "Bearer " + jwt)).andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(content().string(not(containsString(TOKEN_A))));
    }

    @Test
    @DisplayName("El mismo token en otra cuenta pasa a esa cuenta")
    void tokenCambiaDeCuenta() throws Exception {
        registrar(login(ana), TOKEN_A).andExpect(status().isCreated());
        registrar(login(luis), TOKEN_A).andExpect(status().isOk());
        assertThat(dispositivosDe(ana)).isZero();
        assertThat(dispositivosDe(luis)).isEqualTo(1);
    }

    @Test
    @DisplayName("Valida el token y la plataforma")
    void validaciones() throws Exception {
        String jwt = login(ana);
        registrar(jwt, "corto").andExpect(status().isBadRequest());
        registrar(jwt, "token con espacios no permitidos aquí").andExpect(status().isBadRequest());
        registrar(jwt, "x".repeat(513)).andExpect(status().isBadRequest());
        api.perform(post(RUTA).header("Authorization", "Bearer " + jwt).contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + TOKEN_A + "\",\"plataforma\":\"WEB\"}")).andExpect(status().isBadRequest());
        assertThat(dispositivosDe(ana)).isZero();
    }

    @Test
    @DisplayName("Una cuenta no borra ni ve el dispositivo de otra")
    void cuentaAjena() throws Exception {
        String jwtAna = login(ana);
        Number id = JsonPath.read(registrar(jwtAna, TOKEN_A).andReturn().getResponse().getContentAsString(), "$.id");
        String jwtLuis = login(luis);
        api.perform(delete(RUTA + "/" + id).header("Authorization", "Bearer " + jwtLuis)).andExpect(status().isNotFound());
        api.perform(get(RUTA).header("Authorization", "Bearer " + jwtLuis)).andExpect(jsonPath("$", hasSize(0)));
        api.perform(delete(RUTA + "/" + id).header("Authorization", "Bearer " + jwtAna)).andExpect(status().isNoContent());
        assertThat(dispositivosDe(ana)).isZero();
    }

    @Test
    @DisplayName("Como máximo 10 dispositivos por cuenta: el más antiguo se elimina")
    void maximoDiez() throws Exception {
        String jwt = login(ana);
        for (int i = 0; i < 11; i++) registrar(jwt, "fcm-token-limite-AAAAAAAAAAAAAAA-" + i).andExpect(status().isCreated());
        assertThat(dispositivosDe(ana)).isEqualTo(10);
    }

    @Test
    @DisplayName("Cerrar sesión borra el dispositivo de esa sesión; cerrar en todos, todos")
    void cierreDeSesion() throws Exception {
        String sesion1 = login(ana), sesion2 = login(ana);
        registrar(sesion1, TOKEN_A).andExpect(status().isCreated());
        registrar(sesion2, "fcm-token-ana-BBBBBBBBBBBBBBBBBBBBBBBB:2").andExpect(status().isCreated());
        api.perform(post("/api/auth/logout").header("Authorization", "Bearer " + sesion1)).andExpect(status().isOk());
        assertThat(jdbc.queryForList("SELECT token FROM dispositivos", String.class))
                .containsExactly("fcm-token-ana-BBBBBBBBBBBBBBBBBBBBBBBB:2");
        api.perform(post("/api/auth/logout-all").header("Authorization", "Bearer " + sesion2)).andExpect(status().isOk());
        assertThat(dispositivosDe(ana)).isZero();
    }

    @Test
    @DisplayName("El despachador envía cada aviso una vez, solo a la cuenta destinataria y con sus datos")
    void despachaUnaVez() throws Exception {
        registrar(login(ana), TOKEN_A);
        registrar(login(luis), "fcm-token-luis-CCCCCCCCCCCCCCCCCCCCCC:3");
        long id = aviso(ana, "RECLAMO", ahora());
        assertThat(despachador.despachar()).isEqualTo(1);
        assertThat(firebase.enviados).containsExactly(TOKEN_A);
        var m = firebase.mensajes.get(0);
        assertThat(m.notificacionId()).isEqualTo(id);
        assertThat(m.tipo()).isEqualTo("RECLAMO");
        assertThat(m.enlace()).isEqualTo("/objeto/5");
        assertThat(m.titulo()).isEqualTo("Título");
        assertThat(despachador.despachar()).isZero();
        assertThat(jdbc.queryForObject("SELECT push_enviado_en IS NOT NULL FROM notificaciones WHERE id = ?", Boolean.class, id)).isTrue();
    }

    @Test
    @DisplayName("Con la preferencia apagada no se envía; los avisos viejos tampoco")
    void preferenciasYVentana() throws Exception {
        String jwt = login(ana);
        registrar(jwt, TOKEN_A);
        api.perform(put("/api/notificaciones/preferencias").header("Authorization", "Bearer " + jwt)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"coincidencias\":true,\"contactos\":true,\"reclamos\":false}")).andExpect(status().isOk());
        aviso(ana, "RECLAMO", ahora());
        aviso(ana, "COINCIDENCIA_NUEVA", ahora().minusHours(2));
        aviso(ana, "DESCONOCIDO", ahora());
        assertThat(despachador.despachar()).isZero();
        assertThat(firebase.enviados).isEmpty();
        aviso(ana, "MENSAJE_CHAT", ahora());
        assertThat(despachador.despachar()).isEqualTo(1);
    }

    @Test
    @DisplayName("Un token rechazado por Firebase se borra")
    void tokenInvalido() throws Exception {
        registrar(login(ana), TOKEN_A);
        firebase.invalidos.add(TOKEN_A);
        aviso(ana, "SISTEMA", ahora());
        assertThat(despachador.despachar()).isZero();
        assertThat(dispositivosDe(ana)).isZero();
    }

    @Test
    @DisplayName("Sin cuenta de servicio no envía ni marca los avisos, y la API responde igual")
    void sinFirebase() throws Exception {
        firebase.disponible = false;
        registrar(login(ana), TOKEN_A).andExpect(status().isCreated());
        long id = aviso(ana, "SISTEMA", ahora());
        assertThat(despachador.despachar()).isZero();
        assertThat(firebase.enviados).isEmpty();
        assertThat(jdbc.queryForObject("SELECT push_enviado_en IS NULL FROM notificaciones WHERE id = ?", Boolean.class, id)).isTrue();
    }
}
