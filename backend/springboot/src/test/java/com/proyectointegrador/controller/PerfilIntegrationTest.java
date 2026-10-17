package com.proyectointegrador.controller;

import com.proyectointegrador.entity.EstadoUsuario;
import com.proyectointegrador.entity.Rol;
import com.proyectointegrador.entity.Usuario;
import com.proyectointegrador.repository.UsuarioRepository;
import com.proyectointegrador.security.JwtService;
import com.proyectointegrador.security.UserPrincipal;
import com.proyectointegrador.service.EmailService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("H13 - Modificar los datos del perfil propio (API + base temporal)")
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:foundia_perfil_test;MODE=MySQL;DB_CLOSE_DELAY=-1",
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
class PerfilIntegrationTest {

    private static final String PASSWORD = "Segura123!";
    private static final String NUEVA = "Nueva456#";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private JwtService jwtService;
    @MockitoBean
    private EmailService emailService;

    private final PasswordEncoder encoder = new BCryptPasswordEncoder(4);
    private Usuario ana;
    private Usuario luis;
    private String tokenAna;

    @BeforeEach
    void preparar() {
        usuarioRepository.deleteAll();
        ana = usuarioRepository.saveAndFlush(usuario("Ana", "Pérez", "ana@prueba.invalid"));
        luis = usuarioRepository.saveAndFlush(usuario("Luis", "Gómez", "luis@prueba.invalid"));
        tokenAna = jwtService.generateToken(UserPrincipal.from(ana));
    }

    private Usuario usuario(String nombre, String apellido, String correo) {
        Usuario u = new Usuario();
        u.setNombre(nombre);
        u.setApellido(apellido);
        u.setCorreo(correo);
        u.setPassword(encoder.encode(PASSWORD));
        u.setVerificado(true);
        return u;
    }

    private ResultActions actualizar(String token, String json) throws Exception {
        var peticion = put("/api/perfil").contentType(MediaType.APPLICATION_JSON).content(json);
        if (token != null) peticion.header("Authorization", "Bearer " + token);
        return mockMvc.perform(peticion);
    }

    private Usuario recargar(Usuario u) {
        return usuarioRepository.findById(u.getId()).orElseThrow();
    }

    @Test
    @DisplayName("Datos válidos: el mismo valor en la respuesta, en la base y en una nueva consulta")
    void actualizaDatosValidos() throws Exception {
        actualizar(tokenAna, "{\"nombre\":\"  Ána María \",\"apellido\":\"Núñez\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Ána María"))
                .andExpect(jsonPath("$.apellido").value("Núñez"))
                .andExpect(jsonPath("$.password").doesNotExist());

        Usuario guardado = recargar(ana);
        assertThat(guardado.getNombre()).isEqualTo("Ána María");
        assertThat(guardado.getApellido()).isEqualTo("Núñez");

        mockMvc.perform(get("/api/perfil").header("Authorization", "Bearer " + tokenAna))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Ána María"))
                .andExpect(jsonPath("$.apellido").value("Núñez"));
    }

    @Test
    @DisplayName("Sin sesión: 401 y no se modifica nada")
    void sinSesionRechaza() throws Exception {
        actualizar(null, "{\"nombre\":\"Hacker\",\"apellido\":\"Externo\"}")
                .andExpect(status().isUnauthorized());
        assertThat(recargar(ana).getNombre()).isEqualTo("Ana");
        assertThat(recargar(luis).getNombre()).isEqualTo("Luis");
    }

    @Test
    @DisplayName("Datos inválidos: vacío, con números o de más de 100 caracteres dan 400 sin cambios")
    void datosInvalidos() throws Exception {
        actualizar(tokenAna, "{\"nombre\":\"\",\"apellido\":\"Pérez\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(containsString("nombre")));
        actualizar(tokenAna, "{\"nombre\":\"Ana123\",\"apellido\":\"Pérez\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(containsString("letras")));
        actualizar(tokenAna, "{\"nombre\":\"" + "a".repeat(101) + "\",\"apellido\":\"Pérez\"}")
                .andExpect(status().isBadRequest());
        actualizar(tokenAna, "{\"nombre\":\"Ana\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(containsString("apellido")));

        assertThat(recargar(ana).getNombre()).isEqualTo("Ana");
    }

    @Test
    @DisplayName("No permite elevar el rol, cambiar estado, correo ni modificar a otro usuario")
    void noEscalaNiModificaAOtro() throws Exception {
        actualizar(tokenAna, """
                {"id":%d,"nombre":"Ana","apellido":"Pérez","rol":"ADMIN","estado":"ACTIVO",
                 "correo":"otro@prueba.invalid","verificado":true}""".formatted(luis.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(ana.getId()))
                .andExpect(jsonPath("$.rol").value("USUARIO"));

        Usuario guardada = recargar(ana);
        assertThat(guardada.getRol()).isEqualTo(Rol.USUARIO);
        assertThat(guardada.getEstado()).isEqualTo(EstadoUsuario.ACTIVO);
        assertThat(guardada.getCorreo()).isEqualTo("ana@prueba.invalid");
        Usuario otro = recargar(luis);
        assertThat(otro.getNombre()).isEqualTo("Luis");
        assertThat(otro.getCorreo()).isEqualTo("luis@prueba.invalid");
    }

    @Test
    @DisplayName("Cambiar contraseña exige la actual: sin ella o incorrecta da 400 y no cambia nada")
    void cambioPasswordExigeLaActual() throws Exception {
        String hashAntes = recargar(ana).getPassword();

        actualizar(tokenAna, "{\"nombre\":\"Ana\",\"apellido\":\"Pérez\",\"password\":\"" + NUEVA + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(containsString("contraseña actual")));
        actualizar(tokenAna, "{\"nombre\":\"Otro\",\"apellido\":\"Pérez\",\"password\":\"" + NUEVA
                + "\",\"passwordActual\":\"Equivocada1!\"}")
                .andExpect(status().isBadRequest());

        Usuario guardada = recargar(ana);
        assertThat(guardada.getPassword()).isEqualTo(hashAntes);
        assertThat(guardada.getNombre()).isEqualTo("Ana");
    }

    @Test
    @DisplayName("Contraseña nueva débil o de más de 72 bytes: 400 con las mismas reglas del registro")
    void passwordNuevaDebil() throws Exception {
        actualizar(tokenAna, "{\"nombre\":\"Ana\",\"apellido\":\"Pérez\",\"password\":\"corta\",\"passwordActual\":\""
                + PASSWORD + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value(containsString("8 caracteres")));
        actualizar(tokenAna, "{\"nombre\":\"Ana\",\"apellido\":\"Pérez\",\"password\":\"Aa1!" + "ñ".repeat(40)
                + "\",\"passwordActual\":\"" + PASSWORD + "\"}")
                .andExpect(status().isBadRequest());
        assertThat(encoder.matches(PASSWORD, recargar(ana).getPassword())).isTrue();
    }

    @Test
    @DisplayName("Cambio correcto: guarda hash BCrypt, entra con la nueva y no con la anterior")
    void cambioPasswordCorrecto() throws Exception {
        actualizar(tokenAna, "{\"nombre\":\"Ana\",\"apellido\":\"Pérez\",\"password\":\"" + NUEVA
                + "\",\"passwordActual\":\"" + PASSWORD + "\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.password").doesNotExist())
                .andExpect(jsonPath("$.passwordActual").doesNotExist());

        String hash = recargar(ana).getPassword();
        assertThat(hash).startsWith("$2").isNotEqualTo(NUEVA);
        assertThat(encoder.matches(NUEVA, hash)).isTrue();

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\":\"ana@prueba.invalid\",\"password\":\"" + NUEVA + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").exists());
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\":\"ana@prueba.invalid\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Sin contraseña nueva (vacía o ausente) solo se actualizan nombre y apellido")
    void sinPasswordNoLaCambia() throws Exception {
        String hashAntes = recargar(ana).getPassword();
        actualizar(tokenAna, "{\"nombre\":\"Ana\",\"apellido\":\"Ruiz\",\"password\":\"\"}")
                .andExpect(status().isOk());
        assertThat(recargar(ana).getPassword()).isEqualTo(hashAntes);
        assertThat(recargar(ana).getApellido()).isEqualTo("Ruiz");
    }
}
