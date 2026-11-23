package com.proyectointegrador.controller;

import com.jayway.jsonpath.JsonPath;
import com.proyectointegrador.entity.EstadoUsuario;
import com.proyectointegrador.entity.Rol;
import com.proyectointegrador.entity.Usuario;
import com.proyectointegrador.repository.UsuarioRepository;
import com.proyectointegrador.service.EmailService;
import com.sun.net.httpserver.HttpServer;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * H72: token personalizado de Firebase y revocado. Usa una clave RSA de prueba generada aquí y un
 * servidor local que hace de Google; la prueba con el proyecto real del equipo se registra aparte.
 */
@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:firebase_h72;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.main.banner-mode=off", "logging.level.root=WARN", "app.push.retraso-inicial-ms=3600000"})
@AutoConfigureMockMvc
class FirebaseAuthIntegrationTest {

    static final String PASSWORD = "Segura123!";
    static final String CORREO_CUENTA = "firebase-adminsdk@foundia-prueba.iam.gserviceaccount.com";
    static final KeyPair CLAVES;
    static final HttpServer GOOGLE;
    static final List<String> REVOCADOS = new CopyOnWriteArrayList<>();
    static final List<String> AUTORIZACIONES = new CopyOnWriteArrayList<>();

    static {
        try {
            KeyPairGenerator g = KeyPairGenerator.getInstance("RSA");
            g.initialize(2048);
            CLAVES = g.generateKeyPair();
            GOOGLE = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            GOOGLE.createContext("/token", e -> responder(e, "{\"access_token\":\"acceso-prueba\",\"expires_in\":3600}"));
            GOOGLE.createContext("/v1/projects/foundia-prueba/accounts:update", e -> {
                AUTORIZACIONES.add(e.getRequestHeaders().getFirst("Authorization"));
                REVOCADOS.add(new String(e.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
                responder(e, "{}");
            });
            GOOGLE.start();
        } catch (Exception ex) {
            throw new IllegalStateException(ex);
        }
    }

    static void responder(com.sun.net.httpserver.HttpExchange e, String cuerpo) throws java.io.IOException {
        byte[] b = cuerpo.getBytes(StandardCharsets.UTF_8);
        e.getResponseHeaders().add("Content-Type", "application/json");
        e.sendResponseHeaders(200, b.length);
        e.getResponseBody().write(b);
        e.close();
    }

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry r) {
        String base = "http://127.0.0.1:" + GOOGLE.getAddress().getPort();
        String pem = "-----BEGIN PRIVATE KEY-----\n"
                + Base64.getMimeEncoder(64, "\n".getBytes()).encodeToString(CLAVES.getPrivate().getEncoded())
                + "\n-----END PRIVATE KEY-----\n";
        String json = "{\"type\":\"service_account\",\"project_id\":\"foundia-prueba\",\"client_email\":\"" + CORREO_CUENTA
                + "\",\"token_uri\":\"" + base + "/token\",\"private_key\":" + com.fasterxml.jackson.databind.node.TextNode.valueOf(pem) + "}";
        r.add("app.push.firebase-credenciales-json", () -> json);
        r.add("app.firebase.identitytoolkit-url", () -> base);
        r.add("app.push.fcm-url", () -> base);
    }

    @AfterAll
    static void apagar() {
        GOOGLE.stop(0);
    }

    @Autowired MockMvc api;
    @Autowired UsuarioRepository usuarios;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean EmailService email;

    Usuario ana, luis;

    @BeforeEach
    void preparar() {
        jdbc.update("DELETE FROM dispositivos");
        jdbc.update("DELETE FROM tokens_revocados");
        usuarios.deleteAll();
        ana = usuario("Ana", "ana-h72@prueba.invalid");
        luis = usuario("Luis", "luis-h72@prueba.invalid");
        REVOCADOS.clear();
        AUTORIZACIONES.clear();
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

    Map<String, Object> pedirToken(String jwt) throws Exception {
        String r = api.perform(post("/api/auth/firebase-token").header("Authorization", "Bearer " + jwt))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return JsonPath.read(r, "$");
    }

    Claims verificar(String token) {
        return Jwts.parserBuilder().setSigningKey(CLAVES.getPublic()).build().parseClaimsJws(token).getBody();
    }

    @Test
    @DisplayName("La ruta exige sesión aunque /api/auth/** sea público")
    void sinSesion() throws Exception {
        api.perform(post("/api/auth/firebase-token")).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("El token está firmado por la cuenta de servicio, dura una hora y no trae datos personales")
    void tokenPersonalizado() throws Exception {
        Map<String, Object> r = pedirToken(login(ana));
        assertThat(r.get("uid")).isEqualTo("foundia-" + ana.getId());
        Claims c = verificar((String) r.get("token"));
        assertThat(c.getIssuer()).isEqualTo(CORREO_CUENTA);
        assertThat(c.getSubject()).isEqualTo(CORREO_CUENTA);
        assertThat(c.getAudience()).isEqualTo(
                "https://identitytoolkit.googleapis.com/google.identity.identitytoolkit.v1.IdentityToolkit");
        assertThat(c.get("uid")).isEqualTo("foundia-" + ana.getId());
        assertThat(c.getExpiration().getTime() - c.getIssuedAt().getTime()).isEqualTo(3600_000L);
        assertThat(c.get("claims", Map.class)).isEqualTo(Map.of("rol", "USUARIO"));
        String contenido = new String(Base64.getUrlDecoder().decode(((String) r.get("token")).split("\\.")[1]),
                StandardCharsets.UTF_8);
        assertThat(contenido).doesNotContain(ana.getCorreo()).doesNotContain("Ana");
    }

    @Test
    @DisplayName("El UID es estable para la cuenta y distinto entre cuentas")
    void uidEstable() throws Exception {
        String jwt = login(ana);
        Map<String, Object> a = pedirToken(jwt), b = pedirToken(jwt);
        assertThat(a.get("uid")).isEqualTo(b.get("uid"));
        assertThat(pedirToken(login(luis)).get("uid")).isEqualTo("foundia-" + luis.getId()).isNotEqualTo(a.get("uid"));
    }

    @Test
    @DisplayName("Sesión cerrada o cuenta suspendida: no entrega token")
    void sesionCerradaOSuspendida() throws Exception {
        String jwt = login(ana);
        api.perform(post("/api/auth/logout").header("Authorization", "Bearer " + jwt)).andExpect(status().isOk());
        api.perform(post("/api/auth/firebase-token").header("Authorization", "Bearer " + jwt)).andExpect(status().isUnauthorized());

        String deLuis = login(luis);
        luis.setEstado(EstadoUsuario.SUSPENDIDO);
        usuarios.saveAndFlush(luis);
        api.perform(post("/api/auth/firebase-token").header("Authorization", "Bearer " + deLuis)).andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Cerrar en todos los dispositivos revoca Firebase; cerrar una sesión no")
    void revocado() throws Exception {
        api.perform(post("/api/auth/logout").header("Authorization", "Bearer " + login(ana))).andExpect(status().isOk());
        Thread.sleep(800);
        assertThat(REVOCADOS).isEmpty();

        api.perform(post("/api/auth/logout-all").header("Authorization", "Bearer " + login(ana))).andExpect(status().isOk());
        for (int i = 0; i < 50 && REVOCADOS.isEmpty(); i++) Thread.sleep(100);
        assertThat(REVOCADOS).hasSize(1);
        assertThat(REVOCADOS.get(0)).contains("\"localId\":\"foundia-" + ana.getId() + "\"").contains("validSince");
        assertThat(AUTORIZACIONES).containsExactly("Bearer acceso-prueba");
    }
}
