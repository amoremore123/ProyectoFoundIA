package com.proyectointegrador.service;

import com.proyectointegrador.entity.EstadoUsuario;
import com.proyectointegrador.entity.Usuario;
import com.proyectointegrador.repository.UsuarioRepository;
import com.proyectointegrador.security.JwtService;
import com.proyectointegrador.security.UserPrincipal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Timeout;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;

@DisplayName("H11/H12 - Seguridad con API, transacciones y base temporal")
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:foundia_auth_security_test;MODE=MySQL;DB_CLOSE_DELAY=-1",
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
class AuthSecurityIntegrationTest {

    private static final String CORREO = "seguridad@prueba.invalid";
    private static final String LOGIN_INCORRECTO = """
            {"correo":"seguridad@prueba.invalid","password":"Incorrecta123!"}""";

    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private JwtService jwtService;
    @MockitoBean
    private EmailService emailService;

    private Usuario usuario;

    @BeforeEach
    void prepararUsuario() {
        // Esta clase usa una base propia; las escrituras deben ser visibles para otros hilos.
        usuarioRepository.deleteAll();
        usuario = new Usuario();
        usuario.setNombre("Ana");
        usuario.setApellido("Prueba");
        usuario.setCorreo(CORREO);
        usuario.setPassword(new BCryptPasswordEncoder(4).encode("Segura123!"));
        usuario.setVerificado(true);
        usuario = usuarioRepository.saveAndFlush(usuario);
    }

    @Test
    @DisplayName("H12 - El intento fallido se conserva aunque el login termine en 401")
    void intentoFallidoPersistido() throws Exception {
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(LOGIN_INCORRECTO))
                .andExpect(status().isUnauthorized());

        Usuario guardado = usuarioRepository.findByCorreo(CORREO).orElseThrow();
        assertThat(guardado.getIntentosFallidos()).isEqualTo(1);
        assertThat(guardado.getBloqueadoHasta()).isNull();
    }

    @Test
    @Timeout(60)
    @DisplayName("H12 - Ocho intentos simultáneos respetan el límite de cinco y guardan el bloqueo")
    void intentosConcurrentesBloquean() throws Exception {
        int cantidad = 8;
        CyclicBarrier inicio = new CyclicBarrier(cantidad);
        ExecutorService executor = Executors.newFixedThreadPool(cantidad);
        List<Future<Integer>> solicitudes = new ArrayList<>();
        try {
            for (int i = 0; i < cantidad; i++) {
                solicitudes.add(executor.submit(() -> {
                    inicio.await(10, TimeUnit.SECONDS);
                    return mockMvc.perform(post("/api/auth/login")
                                    .contentType(MediaType.APPLICATION_JSON).content(LOGIN_INCORRECTO))
                            .andReturn().getResponse().getStatus();
                }));
            }
            List<Integer> estados = new ArrayList<>();
            for (Future<Integer> solicitud : solicitudes) {
                estados.add(solicitud.get(45, TimeUnit.SECONDS));
            }
            assertThat(estados).containsExactlyInAnyOrder(401, 401, 401, 401, 423, 423, 423, 423);
            Usuario guardado = usuarioRepository.findByCorreo(CORREO).orElseThrow();
            assertThat(guardado.getIntentosFallidos()).isZero();
            assertThat(guardado.getBloqueadoHasta()).isAfter(LocalDateTime.now());
            mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                            .content(LOGIN_INCORRECTO.replace("Incorrecta123!", "Segura123!")))
                    .andExpect(status().isLocked());
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    @DisplayName("H12 - El filtro JWT consulta el estado actual y rechaza una cuenta suspendida")
    void cuentaSuspendidaNoUsaTokenAnterior() throws Exception {
        String token = jwtService.generateToken(UserPrincipal.from(usuario));
        mockMvc.perform(get("/api/perfil").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
        usuario.setEstado(EstadoUsuario.SUSPENDIDO);
        usuarioRepository.saveAndFlush(usuario);

        mockMvc.perform(get("/api/perfil").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(get("/api/categorias").header("Authorization", "Bearer " + token))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("H12 - Un token anterior tampoco habilita una cuenta que no está verificada")
    void cuentaNoVerificadaNoUsaToken() throws Exception {
        String token = jwtService.generateToken(UserPrincipal.from(usuario));
        usuario.setVerificado(false);
        usuarioRepository.saveAndFlush(usuario);

        mockMvc.perform(get("/api/perfil").header("Authorization", "Bearer " + token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("H11/H12 - Verificar una cuenta suspendida devuelve 401 y no consume el código")
    void verificarSuspendidaNoEmiteToken() throws Exception {
        usuario.setVerificado(false);
        usuario.setEstado(EstadoUsuario.SUSPENDIDO);
        usuario.setCodigoVerificacion("123456");
        usuario.setCodigoExpira(LocalDateTime.now().plusMinutes(10));
        usuarioRepository.saveAndFlush(usuario);

        mockMvc.perform(post("/api/auth/verificar").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\":\"seguridad@prueba.invalid\",\"codigo\":\"123456\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.token").doesNotExist());
        Usuario guardado = usuarioRepository.findByCorreo(CORREO).orElseThrow();
        assertThat(guardado.isVerificado()).isFalse();
        assertThat(guardado.getCodigoVerificacion()).isEqualTo("123456");
        assertThat(guardado.getEstado()).isEqualTo(EstadoUsuario.SUSPENDIDO);
    }
}
