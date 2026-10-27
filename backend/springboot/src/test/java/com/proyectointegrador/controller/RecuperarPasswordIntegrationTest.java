package com.proyectointegrador.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.proyectointegrador.entity.EstadoUsuario;
import com.proyectointegrador.entity.Usuario;
import com.proyectointegrador.exception.EnvioCorreoException;
import com.proyectointegrador.repository.TokenRecuperacionPasswordRepository;
import com.proyectointegrador.repository.UsuarioRepository;
import com.proyectointegrador.service.EmailService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:recuperar_password;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "app.web.url=http://web.qa.invalid",
        "spring.main.banner-mode=off", "logging.level.root=WARN"})
@AutoConfigureMockMvc
@DisplayName("H35 - Recuperar contraseña")
class RecuperarPasswordIntegrationTest {

    static final String ANTIGUA = "Antigua123!";
    static final String NUEVA = "Nueva456#";
    static final String MENSAJE = "Si el correo está registrado, te enviamos un enlace para restablecer tu contraseña.";

    @Autowired MockMvc api;
    @Autowired ObjectMapper json;
    @Autowired UsuarioRepository usuarios;
    @Autowired TokenRecuperacionPasswordRepository tokens;
    @Autowired PasswordEncoder encoder;
    @Autowired JdbcTemplate jdbc;
    @MockitoBean EmailService correo;

    @BeforeEach void limpiar() { tokens.deleteAll(); usuarios.deleteAll(); reset(correo); }
    @AfterEach void cerrar() { tokens.deleteAll(); usuarios.deleteAll(); }

    Usuario usuario(String correoUsuario) {
        Usuario u = new Usuario();
        u.setNombre("Ana"); u.setApellido("QA"); u.setCorreo(correoUsuario);
        u.setPassword(encoder.encode(ANTIGUA)); u.setVerificado(true);
        return usuarios.saveAndFlush(u);
    }

    ResultActions postJson(String ruta, Map<String, ?> cuerpo) throws Exception {
        return api.perform(post(ruta).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(cuerpo)));
    }

    ResultActions solicitar(String correoUsuario) throws Exception {
        return postJson("/api/auth/recuperar-password", Map.of("correo", correoUsuario));
    }

    ResultActions restablecer(String token, String password) throws Exception {
        return postJson("/api/auth/restablecer-password", Map.of("token", token, "password", password));
    }

    ResultActions login(String correoUsuario, String password) throws Exception {
        return postJson("/api/auth/login", Map.of("correo", correoUsuario, "password", password));
    }

    /** Devuelve el token del último enlace "enviado" por correo. */
    String tokenEnviado(int vecesEnviado) {
        ArgumentCaptor<String> enlace = ArgumentCaptor.forClass(String.class);
        verify(correo, times(vecesEnviado)).enviarEnlaceRecuperacion(anyString(), anyString(), enlace.capture(), anyLong());
        String url = enlace.getValue();
        assertThat(url).startsWith("http://web.qa.invalid/restablecer-password?token=");
        return url.substring(url.indexOf("token=") + 6);
    }

    @Test
    @DisplayName("Responde igual exista o no la cuenta y solo envía correo a la cuenta real")
    void respuestaNoEnumerable() throws Exception {
        usuario("ana@qa.invalid");

        solicitar("nadie@qa.invalid").andExpect(status().isOk()).andExpect(jsonPath("$.mensaje").value(MENSAJE));
        verify(correo, never()).enviarEnlaceRecuperacion(anyString(), anyString(), anyString(), anyLong());

        solicitar("ANA@qa.invalid").andExpect(status().isOk()).andExpect(jsonPath("$.mensaje").value(MENSAJE));
        verify(correo).enviarEnlaceRecuperacion(eq("ana@qa.invalid"), eq("Ana"), anyString(), eq(30L));
        // Solo se guarda el hash, nunca el token en claro
        String token = tokenEnviado(1);
        assertThat(tokens.findAll()).singleElement()
                .satisfies(t -> assertThat(t.getTokenHash()).hasSize(64).isNotEqualTo(token));
    }

    @Test
    @DisplayName("Cambia la contraseña con el enlace, exige nuevo login y el enlace no se reutiliza")
    void flujoCompletoYUnSoloUso() throws Exception {
        usuario("ana@qa.invalid");
        solicitar("ana@qa.invalid").andExpect(status().isOk());
        String token = tokenEnviado(1);

        restablecer(token, NUEVA).andExpect(status().isOk())
                .andExpect(jsonPath("$.mensaje").value(org.hamcrest.Matchers.containsString("Inicia sesión")));

        login("ana@qa.invalid", ANTIGUA).andExpect(status().isUnauthorized());
        login("ana@qa.invalid", NUEVA).andExpect(status().isOk());
        restablecer(token, "Otra789$").andExpect(status().isBadRequest());
        login("ana@qa.invalid", NUEVA).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Las sesiones abiertas antes del cambio dejan de valer")
    void invalidaSesionesAnteriores() throws Exception {
        usuario("ana@qa.invalid");
        String respuesta = login("ana@qa.invalid", ANTIGUA).andReturn().getResponse().getContentAsString();
        String jwtAnterior = json.readTree(respuesta).get("token").asText();
        api.perform(get("/api/perfil").header("Authorization", "Bearer " + jwtAnterior)).andExpect(status().isOk());

        Thread.sleep(1100); // el JWT guarda la emisión en segundos
        solicitar("ana@qa.invalid");
        restablecer(tokenEnviado(1), NUEVA).andExpect(status().isOk());

        api.perform(get("/api/perfil").header("Authorization", "Bearer " + jwtAnterior))
                .andExpect(status().isUnauthorized());
        Thread.sleep(1100);
        String nueva = login("ana@qa.invalid", NUEVA).andReturn().getResponse().getContentAsString();
        api.perform(get("/api/perfil").header("Authorization", "Bearer " + json.readTree(nueva).get("token").asText()))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Un enlace vencido, inventado o reemplazado por otro más nuevo se rechaza")
    void enlacesInvalidos() throws Exception {
        usuario("ana@qa.invalid");
        solicitar("ana@qa.invalid");
        String primero = tokenEnviado(1);

        restablecer("TokenInventado_1234567890", NUEVA).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje").value("El enlace no es válido o ya venció. Solicita uno nuevo."));

        // Vencido
        // creado_en/expira_en no son actualizables desde JPA: se ajustan directo en la tabla
        jdbc.update("UPDATE tokens_recuperacion_password SET expira_en = ?",
                LocalDateTime.now(ZoneOffset.UTC).minusMinutes(1));
        restablecer(primero, NUEVA).andExpect(status().isBadRequest());

        // Una solicitud nueva (pasada la espera) reemplaza al enlace anterior
        jdbc.update("UPDATE tokens_recuperacion_password SET creado_en = ?, expira_en = ?",
                LocalDateTime.now(ZoneOffset.UTC).minusMinutes(5), LocalDateTime.now(ZoneOffset.UTC).plusMinutes(10));
        solicitar("ana@qa.invalid");
        String segundo = tokenEnviado(2);
        restablecer(primero, NUEVA).andExpect(status().isBadRequest());
        restablecer(segundo, NUEVA).andExpect(status().isOk());
    }

    @Test
    @DisplayName("Pedidos seguidos no envían otro correo durante la espera")
    void esperaEntreSolicitudes() throws Exception {
        usuario("ana@qa.invalid");
        solicitar("ana@qa.invalid").andExpect(status().isOk());
        solicitar("ana@qa.invalid").andExpect(status().isOk()).andExpect(jsonPath("$.mensaje").value(MENSAJE));
        verify(correo, times(1)).enviarEnlaceRecuperacion(anyString(), anyString(), anyString(), anyLong());
    }

    @Test
    @DisplayName("Una contraseña débil se rechaza sin consumir el enlace")
    void passwordDebilNoConsumeEnlace() throws Exception {
        usuario("ana@qa.invalid");
        solicitar("ana@qa.invalid");
        String token = tokenEnviado(1);

        restablecer(token, "123456").andExpect(status().isBadRequest());
        restablecer(token, NUEVA).andExpect(status().isOk());
    }

    @Test
    @DisplayName("No reactiva ni cambia la contraseña de una cuenta suspendida")
    void cuentaSuspendida() throws Exception {
        Usuario u = usuario("ana@qa.invalid");
        solicitar("ana@qa.invalid");
        String token = tokenEnviado(1);

        u = usuarios.findById(u.getId()).orElseThrow();
        u.setEstado(EstadoUsuario.SUSPENDIDO);
        usuarios.saveAndFlush(u);

        restablecer(token, NUEVA).andExpect(status().isBadRequest());
        Usuario despues = usuarios.findById(u.getId()).orElseThrow();
        assertThat(despues.getEstado()).isEqualTo(EstadoUsuario.SUSPENDIDO);
        assertThat(encoder.matches(ANTIGUA, despues.getPassword())).isTrue();

        // Una cuenta ya suspendida tampoco recibe enlaces (misma respuesta)
        solicitar("ana@qa.invalid").andExpect(status().isOk()).andExpect(jsonPath("$.mensaje").value(MENSAJE));
        verify(correo, times(1)).enviarEnlaceRecuperacion(anyString(), anyString(), anyString(), anyLong());
    }

    @Test
    @DisplayName("Si el correo falla, la respuesta es la misma y el enlace no sirve")
    void falloDeCorreo() throws Exception {
        usuario("ana@qa.invalid");
        doThrow(new EnvioCorreoException("fallo")).when(correo)
                .enviarEnlaceRecuperacion(anyString(), anyString(), anyString(), anyLong());

        solicitar("ana@qa.invalid").andExpect(status().isOk()).andExpect(jsonPath("$.mensaje").value(MENSAJE));
        String token = tokenEnviado(1);
        restablecer(token, NUEVA).andExpect(status().isBadRequest());
    }
}
