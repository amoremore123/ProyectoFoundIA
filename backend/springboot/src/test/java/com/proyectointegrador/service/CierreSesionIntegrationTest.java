package com.proyectointegrador.service;

import com.jayway.jsonpath.JsonPath;
import com.proyectointegrador.entity.EstadoUsuario;
import com.proyectointegrador.entity.Rol;
import com.proyectointegrador.entity.TokenRevocado;
import com.proyectointegrador.entity.Usuario;
import com.proyectointegrador.repository.TokenRevocadoRepository;
import com.proyectointegrador.repository.UsuarioRepository;
import com.proyectointegrador.security.JwtService;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import io.jsonwebtoken.security.Keys;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Date;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("H37 - Cierre de sesión seguro")
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:foundia_cierre_sesion_test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.main.banner-mode=off",
        "logging.level.root=WARN"
})
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class CierreSesionIntegrationTest {

    private static final String CORREO = "ana-h37@prueba.invalid";
    private static final String OTRO_CORREO = "luis-h37@prueba.invalid";
    private static final String PASSWORD = "Segura123!";

    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private TokenRevocadoRepository tokenRevocadoRepository;
    @Autowired
    private JwtService jwtService;
    @Autowired
    private MockMvc mockMvc;
    @MockitoBean
    private EmailService emailService;
    @Value("${app.jwt.secret}")
    private String secret;

    private Usuario ana;
    private Usuario luis;

    @BeforeEach
    void prepararUsuarios() {
        tokenRevocadoRepository.deleteAll();
        usuarioRepository.deleteAll();
        ana = usuario("Ana", "QA", CORREO);
        luis = usuario("Luis", "QA", OTRO_CORREO);
    }

    private Usuario usuario(String nombre, String apellido, String correo) {
        Usuario usuario = new Usuario();
        usuario.setNombre(nombre);
        usuario.setApellido(apellido);
        usuario.setCorreo(correo);
        usuario.setPassword(new BCryptPasswordEncoder(4).encode(PASSWORD));
        usuario.setRol(Rol.USUARIO);
        usuario.setEstado(EstadoUsuario.ACTIVO);
        usuario.setVerificado(true);
        return usuarioRepository.saveAndFlush(usuario);
    }

    private String login(String correo) throws Exception {
        String respuesta = mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\":\"" + correo + "\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        return JsonPath.read(respuesta, "$.token");
    }

    private int perfil(String token) throws Exception {
        return mockMvc.perform(get("/api/perfil").header("Authorization", "Bearer " + token))
                .andReturn().getResponse().getStatus();
    }

    @Test
    @DisplayName("El login emite jti y versión, y el cierre revoca solo el token usado")
    void cierreIndividualRevocaSoloEseToken() throws Exception {
        String primero = login(CORREO);
        String segundo = login(CORREO);
        String jti = jwtService.extractJti(primero);

        assertThat(jti).isNotBlank();
        assertThat(perfil(primero)).isEqualTo(200);
        assertThat(perfil(segundo)).isEqualTo(200);

        mockMvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + primero))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensaje").value(containsString("cerrada")));

        assertThat(tokenRevocadoRepository.existsByJti(jti)).isTrue();
        TokenRevocado revocado = tokenRevocadoRepository.findById(jti).orElseThrow();
        assertThat(revocado.getExpiraEn()).isAfter(LocalDateTime.now(ZoneOffset.UTC));
        assertThat(perfil(primero)).isEqualTo(401);
        assertThat(perfil(segundo)).isEqualTo(200);
    }

    @Test
    @DisplayName("Cerrar todas invalida la cuenta y no afecta a otra cuenta")
    void cierreGlobalNoAfectaAOtraCuenta() throws Exception {
        String primero = login(CORREO);
        String segundo = login(CORREO);
        String ajeno = login(OTRO_CORREO);

        mockMvc.perform(post("/api/auth/logout-all").header("Authorization", "Bearer " + primero))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensaje").value(containsString("dispositivos")));

        assertThat(usuarioRepository.findById(ana.getId()).orElseThrow().getSesionVersion()).isEqualTo(1);
        assertThat(perfil(primero)).isEqualTo(401);
        assertThat(perfil(segundo)).isEqualTo(401);
        assertThat(perfil(ajeno)).isEqualTo(200);

        String nuevo = login(CORREO);
        assertThat(perfil(nuevo)).isEqualTo(200);
    }

    @Test
    @DisplayName("Un token legado sin jti ni versión se acepta en versión 0 y el cierre lo invalida")
    void tokenLegadoSinJtiCierraTodasLasSesiones() throws Exception {
        String legado = Jwts.builder()
                .setSubject(CORREO)
                .setIssuedAt(new Date())
                .setExpiration(new Date(System.currentTimeMillis() + 60_000))
                .signWith(Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8)),
                        SignatureAlgorithm.HS256)
                .compact();

        assertThat(perfil(legado)).isEqualTo(200);
        assertThat(jwtService.extractJti(legado)).isNull();
        mockMvc.perform(post("/api/auth/logout").header("Authorization", "Bearer " + legado))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mensaje").value(containsString("dispositivos")));

        assertThat(usuarioRepository.findById(ana.getId()).orElseThrow().getSesionVersion()).isEqualTo(1);
        assertThat(perfil(legado)).isEqualTo(401);
        assertThat(perfil(login(CORREO))).isEqualTo(200);
    }

    @Test
    @DisplayName("Un jti revocado o una versión anterior no autentican")
    void tokenRevocadoOVersionAnteriorNoAutentica() throws Exception {
        String token = login(CORREO);
        String jti = jwtService.extractJti(token);

        tokenRevocadoRepository.saveAndFlush(new TokenRevocado(
                jti, ana, LocalDateTime.now(ZoneOffset.UTC),
                LocalDateTime.now(ZoneOffset.UTC).plusHours(1)));
        assertThat(perfil(token)).isEqualTo(401);

        String segundo = login(CORREO);
        ana.setSesionVersion(ana.getSesionVersion() + 1);
        usuarioRepository.saveAndFlush(ana);
        assertThat(perfil(segundo)).isEqualTo(401);
    }

    @Test
    @DisplayName("El cierre de sesión exige un token válido")
    void cierreSinTokenEs401() throws Exception {
        mockMvc.perform(post("/api/auth/logout")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/auth/logout-all")).andExpect(status().isUnauthorized());
    }
}
