package com.proyectointegrador.controller;

import com.jayway.jsonpath.JsonPath;
import com.proyectointegrador.entity.EstadoUsuario;
import com.proyectointegrador.entity.Rol;
import com.proyectointegrador.entity.Usuario;
import com.proyectointegrador.exception.EnvioCorreoException;
import com.proyectointegrador.repository.UsuarioRepository;
import com.proyectointegrador.service.DespachadorCorreo;
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
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.reset;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// H68: elegir el canal de los avisos (push y correo). Correo y Firebase simulados.
@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:canal_h68;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.main.banner-mode=off", "logging.level.root=WARN",
        // Los despachadores programados no corren durante la prueba: se llaman a mano
        "app.push.retraso-inicial-ms=3600000", "app.correo-avisos.retraso-inicial-ms=3600000",
        "app.web.url=https://foundia.test/"})
@AutoConfigureMockMvc
class CanalAvisosIntegrationTest {

    static final String RUTA = "/api/notificaciones/preferencias";
    static final String PASSWORD = "Segura123!";
    static final String TOKEN = "fcm-token-ana-AAAAAAAAAAAAAAAAAAAAAAAA:1";

    static class FirebaseSimulado implements EnviadorPush {
        final List<String> enviados = new ArrayList<>();
        public boolean disponible() { return true; }
        public Resultado enviar(String token, Mensaje m) { enviados.add(token); return Resultado.ENVIADO; }
    }

    @TestConfiguration
    static class Configuracion {
        @Bean @Primary FirebaseSimulado firebaseSimulado() { return new FirebaseSimulado(); }
    }

    @Autowired MockMvc api;
    @Autowired UsuarioRepository usuarios;
    @Autowired JdbcTemplate jdbc;
    @Autowired DespachadorPush push;
    @Autowired DespachadorCorreo correo;
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
        ana = usuario("Ana", "ana-h68@prueba.invalid");
        luis = usuario("Luis", "luis-h68@prueba.invalid");
        firebase.enviados.clear();
        reset(email);
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

    ResultActions guardar(String jwt, String cuerpo) throws Exception {
        return api.perform(put(RUTA).header("Authorization", "Bearer " + jwt)
                .contentType(MediaType.APPLICATION_JSON).content(cuerpo));
    }

    void canales(Usuario u, boolean push, boolean correo) throws Exception {
        guardar(login(u), "{\"coincidencias\":true,\"contactos\":true,\"reclamos\":true,\"push\":" + push
                + ",\"correo\":" + correo + "}").andExpect(status().isOk());
    }

    long aviso(Usuario u, String tipo, String enlace, LocalDateTime creado) {
        jdbc.update("INSERT INTO notificaciones (usuario_id, titulo, mensaje, tipo, enlace, leida, fecha_creacion) "
                + "VALUES (?, 'Nueva coincidencia', 'Hay una nueva coincidencia para tu publicación #5.', ?, ?, FALSE, ?)",
                u.getId(), tipo, enlace, Timestamp.valueOf(creado));
        return jdbc.queryForObject("SELECT MAX(id) FROM notificaciones", Long.class);
    }

    long aviso(Usuario u, String tipo) { return aviso(u, tipo, "/objeto/5", ahora()); }

    String resultado(long id) {
        return jdbc.queryForObject("SELECT correo_resultado FROM notificaciones WHERE id = ?", String.class, id);
    }

    LocalDateTime ahora() { return LocalDateTime.now(ZoneOffset.UTC); }

    @Test
    @DisplayName("Por defecto push sí y correo no; se guardan y se leen los dos canales")
    void preferencias() throws Exception {
        String jwt = login(ana);
        api.perform(get(RUTA).header("Authorization", "Bearer " + jwt)).andExpect(status().isOk())
                .andExpect(jsonPath("$.push").value(true)).andExpect(jsonPath("$.correo").value(false));
        guardar(jwt, "{\"coincidencias\":true,\"contactos\":false,\"reclamos\":true,\"push\":false,\"correo\":true}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.push").value(false))
                .andExpect(jsonPath("$.correo").value(true)).andExpect(jsonPath("$.contactos").value(false));
        api.perform(get(RUTA).header("Authorization", "Bearer " + jwt))
                .andExpect(jsonPath("$.push").value(false)).andExpect(jsonPath("$.correo").value(true));
        // Las preferencias son de cada cuenta
        api.perform(get(RUTA).header("Authorization", "Bearer " + login(luis)))
                .andExpect(jsonPath("$.push").value(true)).andExpect(jsonPath("$.correo").value(false));
    }

    @Test
    @DisplayName("Un cliente de H24/H53 que no envía los canales conserva los guardados")
    void clienteAnteriorConservaLosCanales() throws Exception {
        String jwt = login(ana);
        guardar(jwt, "{\"coincidencias\":true,\"contactos\":true,\"reclamos\":true,\"push\":false,\"correo\":true}")
                .andExpect(status().isOk());
        guardar(jwt, "{\"coincidencias\":false,\"contactos\":true,\"reclamos\":true}").andExpect(status().isOk())
                .andExpect(jsonPath("$.coincidencias").value(false))
                .andExpect(jsonPath("$.push").value(false)).andExpect(jsonPath("$.correo").value(true));
        guardar(jwt, "{\"coincidencias\":false,\"contactos\":true,\"reclamos\":true,\"recordatorios\":false}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.correo").value(true));
    }

    @Test
    @DisplayName("Un canal que no es booleano responde 400 y sin sesión 401")
    void validaciones() throws Exception {
        String jwt = login(ana);
        guardar(jwt, "{\"coincidencias\":true,\"contactos\":true,\"reclamos\":true,\"correo\":\"siempre\"}")
                .andExpect(status().isBadRequest());
        guardar(jwt, "{\"coincidencias\":true,\"contactos\":true,\"reclamos\":true,\"push\":[]}")
                .andExpect(status().isBadRequest());
        api.perform(put(RUTA).contentType(MediaType.APPLICATION_JSON)
                .content("{\"coincidencias\":true,\"contactos\":true,\"reclamos\":true,\"correo\":true}"))
                .andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM preferencias_notificacion", Integer.class)).isZero();
    }

    @Test
    @DisplayName("Con push apagado no se envía al teléfono, pero el aviso queda en la bandeja")
    void pushApagado() throws Exception {
        String jwt = login(ana);
        api.perform(post("/api/dispositivos").header("Authorization", "Bearer " + jwt).contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + TOKEN + "\",\"plataforma\":\"ANDROID\"}")).andExpect(status().isCreated());
        canales(ana, false, false);
        aviso(ana, "COINCIDENCIA_NUEVA");
        assertThat(push.despachar()).isZero();
        assertThat(firebase.enviados).isEmpty();
        api.perform(get("/api/notificaciones").header("Authorization", "Bearer " + jwt)).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].titulo").value("Nueva coincidencia"));

        canales(ana, true, false);
        aviso(ana, "COINCIDENCIA_NUEVA");
        assertThat(push.despachar()).isEqualTo(1);
        assertThat(firebase.enviados).containsExactly(TOKEN);
    }

    @Test
    @DisplayName("Con correo activo llega un correo por aviso, con el enlace a la web, una sola vez")
    void correoActivo() throws Exception {
        canales(ana, true, true);
        long id = aviso(ana, "COINCIDENCIA_NUEVA");
        assertThat(correo.despachar()).isEqualTo(1);
        verify(email).enviarAviso(eq("ana-h68@prueba.invalid"), eq("Ana"), eq("Nueva coincidencia"),
                eq("Hay una nueva coincidencia para tu publicación #5."), eq("https://foundia.test/notificaciones"));
        assertThat(resultado(id)).isEqualTo("ENVIADO");
        // Una segunda vuelta no lo vuelve a enviar
        assertThat(correo.despachar()).isZero();
        verify(email, times(1)).enviarAviso(anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Sin el canal, con el tipo apagado o con un tipo desconocido no se envía correo")
    void correoOmitido() throws Exception {
        long sinCanal = aviso(luis, "COINCIDENCIA_NUEVA");  // luis no activó el correo
        guardar(login(ana), "{\"coincidencias\":false,\"contactos\":true,\"reclamos\":true,\"correo\":true}")
                .andExpect(status().isOk());
        long tipoApagado = aviso(ana, "COINCIDENCIA_NUEVA");
        long desconocido = aviso(ana, "OTRO_TIPO");
        long contacto = aviso(ana, "CONTACTO_RECIBIDO");
        assertThat(correo.despachar()).isEqualTo(1);
        assertThat(resultado(sinCanal)).isEqualTo("OMITIDO");
        assertThat(resultado(tipoApagado)).isEqualTo("OMITIDO");
        assertThat(resultado(desconocido)).isEqualTo("OMITIDO");
        assertThat(resultado(contacto)).isEqualTo("ENVIADO");
        verify(email, times(1)).enviarAviso(anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Una cuenta suspendida o sin verificar no recibe correos")
    void cuentaQueNoPuedeRecibir() throws Exception {
        canales(ana, true, true);
        canales(luis, true, true);
        jdbc.update("UPDATE usuarios SET estado = 'SUSPENDIDO' WHERE id = ?", ana.getId());
        jdbc.update("UPDATE usuarios SET verificado = FALSE WHERE id = ?", luis.getId());
        long a = aviso(ana, "COINCIDENCIA_NUEVA");
        long l = aviso(luis, "COINCIDENCIA_NUEVA");
        assertThat(correo.despachar()).isZero();
        assertThat(resultado(a)).isEqualTo("OMITIDO");
        assertThat(resultado(l)).isEqualTo("OMITIDO");
        verify(email, never()).enviarAviso(anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Un aviso viejo no se envía por correo al activar el canal")
    void avisoViejo() throws Exception {
        canales(ana, true, true);
        long viejo = aviso(ana, "COINCIDENCIA_NUEVA", "/objeto/5", ahora().minusHours(2));
        assertThat(correo.despachar()).isZero();
        assertThat(resultado(viejo)).isNull();
        verify(email, never()).enviarAviso(anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Chat: un correo por conversación cada 30 minutos, con el enlace a la conversación de la web")
    void chatAgrupado() throws Exception {
        canales(ana, true, true);
        long primero = aviso(ana, "MENSAJE_CHAT", "/conversaciones/7", ahora());
        assertThat(correo.despachar()).isEqualTo(1);
        verify(email).enviarAviso(anyString(), anyString(), anyString(), anyString(), eq("https://foundia.test/mensajes/7"));
        long segundo = aviso(ana, "MENSAJE_CHAT", "/conversaciones/7", ahora());
        long otraConversacion = aviso(ana, "MENSAJE_CHAT", "/conversaciones/8", ahora());
        assertThat(correo.despachar()).isEqualTo(1);
        assertThat(resultado(primero)).isEqualTo("ENVIADO");
        assertThat(resultado(segundo)).isEqualTo("OMITIDO");
        assertThat(resultado(otraConversacion)).isEqualTo("ENVIADO");

        // Pasados 30 minutos desde el último correo de esa conversación, vuelve a avisar
        jdbc.update("UPDATE notificaciones SET correo_enviado_en = ? WHERE id = ?",
                Timestamp.valueOf(ahora().minusMinutes(31)), primero);
        long despues = aviso(ana, "MENSAJE_CHAT", "/conversaciones/7", ahora());
        assertThat(correo.despachar()).isEqualTo(1);
        assertThat(resultado(despues)).isEqualTo("ENVIADO");
    }

    @Test
    @DisplayName("Si el servidor de correo falla, el aviso queda en ERROR y no se reintenta")
    void falloDelCorreo() throws Exception {
        canales(ana, true, true);
        doThrow(new EnvioCorreoException("caído")).when(email)
                .enviarAviso(anyString(), anyString(), anyString(), anyString(), anyString());
        long id = aviso(ana, "COINCIDENCIA_NUEVA");
        assertThat(correo.despachar()).isZero();
        assertThat(resultado(id)).isEqualTo("ERROR");
        assertThat(correo.despachar()).isZero();
        verify(email, times(1)).enviarAviso(anyString(), anyString(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("Los avisos que crea Django (RECLAMO, SISTEMA) también salen por correo")
    void avisosDeDjango() throws Exception {
        canales(ana, true, true);
        long reclamo = aviso(ana, "RECLAMO", null, ahora());
        long sistema = aviso(ana, "SISTEMA", null, ahora());
        assertThat(correo.despachar()).isEqualTo(2);
        assertThat(resultado(reclamo)).isEqualTo("ENVIADO");
        assertThat(resultado(sistema)).isEqualTo("ENVIADO");
    }
}
