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

    static final String TIPOS = "\"coincidencias\":true,\"contactos\":true,\"reclamos\":true";

    /** Guarda solo los canales indicados, por ejemplo {"coincidencias":"CORREO"}. */
    void canales(Usuario u, String canalesJson) throws Exception {
        guardar(login(u), "{" + TIPOS + ",\"canales\":" + canalesJson + "}").andExpect(status().isOk());
    }

    /** El mismo canal para los 6 tipos de aviso. */
    void todos(Usuario u, String canal) throws Exception {
        String c = "\"" + canal + "\"";
        canales(u, "{\"coincidencias\":" + c + ",\"contactos\":" + c + ",\"reclamos\":" + c
                + ",\"recordatorios\":" + c + ",\"mensajes\":" + c + ",\"sistema\":" + c + "}");
    }

    void registrarTelefono(Usuario u) throws Exception {
        api.perform(post("/api/dispositivos").header("Authorization", "Bearer " + login(u)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + TOKEN + "\",\"plataforma\":\"ANDROID\"}")).andExpect(status().isCreated());
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
    @DisplayName("Por defecto todo es PUSH; se guardan y se leen los canales de cada tipo")
    void preferencias() throws Exception {
        String jwt = login(ana);
        api.perform(get(RUTA).header("Authorization", "Bearer " + jwt)).andExpect(status().isOk())
                .andExpect(jsonPath("$.canales.coincidencias").value("PUSH"))
                .andExpect(jsonPath("$.canales.contactos").value("PUSH"))
                .andExpect(jsonPath("$.canales.reclamos").value("PUSH"))
                .andExpect(jsonPath("$.canales.recordatorios").value("PUSH"))
                .andExpect(jsonPath("$.canales.mensajes").value("PUSH"))
                .andExpect(jsonPath("$.canales.sistema").value("PUSH"));
        guardar(jwt, "{\"coincidencias\":true,\"contactos\":false,\"reclamos\":true,\"canales\":"
                + "{\"coincidencias\":\"CORREO\",\"contactos\":\"AMBOS\",\"mensajes\":\"NINGUNO\"}}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.contactos").value(false))
                .andExpect(jsonPath("$.canales.coincidencias").value("CORREO"))
                .andExpect(jsonPath("$.canales.contactos").value("AMBOS"))
                .andExpect(jsonPath("$.canales.mensajes").value("NINGUNO"))
                // Las claves que no llegan conservan lo guardado
                .andExpect(jsonPath("$.canales.reclamos").value("PUSH"))
                .andExpect(jsonPath("$.canales.sistema").value("PUSH"));
        // Enviar una sola clave cambia solo esa
        guardar(jwt, "{" + TIPOS + ",\"canales\":{\"sistema\":\"CORREO\"}}").andExpect(status().isOk())
                .andExpect(jsonPath("$.canales.sistema").value("CORREO"))
                .andExpect(jsonPath("$.canales.coincidencias").value("CORREO"))
                .andExpect(jsonPath("$.canales.mensajes").value("NINGUNO"));
        // Las preferencias son de cada cuenta
        api.perform(get(RUTA).header("Authorization", "Bearer " + login(luis)))
                .andExpect(jsonPath("$.canales.coincidencias").value("PUSH"));
    }

    @Test
    @DisplayName("Un cliente de H24/H53 que no envía canales conserva los guardados")
    void clienteAnteriorConservaLosCanales() throws Exception {
        String jwt = login(ana);
        todos(ana, "CORREO");
        guardar(jwt, "{\"coincidencias\":false,\"contactos\":true,\"reclamos\":true}").andExpect(status().isOk())
                .andExpect(jsonPath("$.coincidencias").value(false))
                .andExpect(jsonPath("$.canales.coincidencias").value("CORREO"))
                .andExpect(jsonPath("$.canales.mensajes").value("CORREO"));
        guardar(jwt, "{\"coincidencias\":false,\"contactos\":true,\"reclamos\":true,\"recordatorios\":false}")
                .andExpect(status().isOk()).andExpect(jsonPath("$.canales.recordatorios").value("CORREO"));
    }

    @Test
    @DisplayName("Un canal inválido, una clave desconocida o un valor no booleano responden 400; sin sesión 401")
    void validaciones() throws Exception {
        String jwt = login(ana);
        for (String malo : new String[]{
                "{" + TIPOS + ",\"canales\":{\"coincidencias\":\"SIEMPRE\"}}",
                "{" + TIPOS + ",\"canales\":{\"coincidencias\":\"push\"}}",
                "{" + TIPOS + ",\"canales\":{\"chat\":\"CORREO\"}}",
                "{" + TIPOS + ",\"canales\":{\"coincidencias\":true}}",
                "{" + TIPOS + ",\"canales\":\"CORREO\"}",
                "{\"coincidencias\":\"si\",\"contactos\":true,\"reclamos\":true}"}) {
            assertThat(guardar(jwt, malo).andReturn().getResponse().getStatus()).as(malo).isEqualTo(400);
        }
        api.perform(put(RUTA).contentType(MediaType.APPLICATION_JSON)
                .content("{" + TIPOS + ",\"canales\":{\"coincidencias\":\"CORREO\"}}"))
                .andExpect(status().isUnauthorized());
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM preferencias_notificacion", Integer.class)).isZero();
    }

    @Test
    @DisplayName("Con el tipo en «solo correo» o NINGUNO no se manda push; el aviso queda en la bandeja")
    void soloCorreoNoMandaPush() throws Exception {
        registrarTelefono(ana);
        canales(ana, "{\"coincidencias\":\"CORREO\",\"contactos\":\"NINGUNO\"}");
        aviso(ana, "COINCIDENCIA_NUEVA");
        aviso(ana, "CONTACTO_RECIBIDO");
        assertThat(push.despachar()).isZero();
        assertThat(firebase.enviados).isEmpty();
        api.perform(get("/api/notificaciones").header("Authorization", "Bearer " + login(ana))).andExpect(status().isOk())
                .andExpect(jsonPath("$", org.hamcrest.Matchers.hasSize(2)));

        // El canal de un tipo no afecta a los demás: los mensajes siguen en PUSH
        aviso(ana, "MENSAJE_CHAT", "/conversaciones/7", ahora());
        assertThat(push.despachar()).isEqualTo(1);
        // Y con AMBOS vuelve a salir por push
        canales(ana, "{\"coincidencias\":\"AMBOS\"}");
        aviso(ana, "COINCIDENCIA_NUEVA");
        assertThat(push.despachar()).isEqualTo(1);
        assertThat(firebase.enviados).containsExactly(TOKEN, TOKEN);
    }

    @Test
    @DisplayName("Con CORREO o AMBOS llega un correo por aviso, con el enlace a la web, una sola vez")
    void correoActivo() throws Exception {
        canales(ana, "{\"coincidencias\":\"CORREO\",\"contactos\":\"AMBOS\"}");
        long id = aviso(ana, "COINCIDENCIA_NUEVA");
        long contacto = aviso(ana, "CONTACTO_RECIBIDO");
        assertThat(correo.despachar()).isEqualTo(2);
        // Los dos avisos de prueba tienen el mismo título y texto: la misma llamada, dos veces
        verify(email, times(2)).enviarAviso(eq("ana-h68@prueba.invalid"), eq("Ana"), eq("Nueva coincidencia"),
                eq("Hay una nueva coincidencia para tu publicación #5."), eq("https://foundia.test/notificaciones"));
        assertThat(resultado(id)).isEqualTo("ENVIADO");
        assertThat(resultado(contacto)).isEqualTo("ENVIADO");
        // Una segunda vuelta no los vuelve a enviar
        assertThat(correo.despachar()).isZero();
        verify(email, times(2)).enviarAviso(anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Con PUSH, NINGUNO, el tipo apagado o un tipo desconocido no se envía correo")
    void correoOmitido() throws Exception {
        long porDefecto = aviso(luis, "COINCIDENCIA_NUEVA");  // luis tiene todo en PUSH
        guardar(login(ana), "{\"coincidencias\":false,\"contactos\":true,\"reclamos\":true,\"canales\":"
                + "{\"coincidencias\":\"CORREO\",\"contactos\":\"CORREO\",\"reclamos\":\"NINGUNO\"}}")
                .andExpect(status().isOk());
        long tipoApagado = aviso(ana, "COINCIDENCIA_NUEVA");
        long ninguno = aviso(ana, "RECLAMO_RECIBIDO");
        long soloPush = aviso(ana, "MENSAJE_CHAT", "/conversaciones/7", ahora());
        long desconocido = aviso(ana, "OTRO_TIPO");
        long contacto = aviso(ana, "CONTACTO_RECIBIDO");
        assertThat(correo.despachar()).isEqualTo(1);
        assertThat(resultado(porDefecto)).isEqualTo("OMITIDO");
        assertThat(resultado(tipoApagado)).isEqualTo("OMITIDO");
        assertThat(resultado(ninguno)).isEqualTo("OMITIDO");
        assertThat(resultado(soloPush)).isEqualTo("OMITIDO");
        assertThat(resultado(desconocido)).isEqualTo("OMITIDO");
        assertThat(resultado(contacto)).isEqualTo("ENVIADO");
        verify(email, times(1)).enviarAviso(anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Una cuenta suspendida o sin verificar no recibe correos")
    void cuentaQueNoPuedeRecibir() throws Exception {
        todos(ana, "AMBOS");
        todos(luis, "CORREO");
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
        todos(ana, "CORREO");
        long viejo = aviso(ana, "COINCIDENCIA_NUEVA", "/objeto/5", ahora().minusHours(2));
        assertThat(correo.despachar()).isZero();
        assertThat(resultado(viejo)).isNull();
        verify(email, never()).enviarAviso(anyString(), anyString(), anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("Chat: un correo por conversación cada 30 minutos, con el enlace a la conversación de la web")
    void chatAgrupado() throws Exception {
        canales(ana, "{\"mensajes\":\"CORREO\"}");
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
        todos(ana, "CORREO");
        doThrow(new EnvioCorreoException("caído")).when(email)
                .enviarAviso(anyString(), anyString(), anyString(), anyString(), anyString());
        long id = aviso(ana, "COINCIDENCIA_NUEVA");
        assertThat(correo.despachar()).isZero();
        assertThat(resultado(id)).isEqualTo("ERROR");
        assertThat(correo.despachar()).isZero();
        verify(email, times(1)).enviarAviso(anyString(), anyString(), anyString(), anyString(), any());
    }

    @Test
    @DisplayName("Los avisos que crea Django (RECLAMO, SISTEMA) siguen el canal de su tipo")
    void avisosDeDjango() throws Exception {
        canales(ana, "{\"reclamos\":\"CORREO\",\"sistema\":\"AMBOS\"}");
        long reclamo = aviso(ana, "RECLAMO", null, ahora());
        long sistema = aviso(ana, "SISTEMA", null, ahora());
        assertThat(correo.despachar()).isEqualTo(2);
        assertThat(resultado(reclamo)).isEqualTo("ENVIADO");
        assertThat(resultado(sistema)).isEqualTo("ENVIADO");
    }
}
