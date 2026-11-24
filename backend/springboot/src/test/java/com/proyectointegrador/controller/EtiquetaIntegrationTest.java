package com.proyectointegrador.controller;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.proyectointegrador.entity.Rol;
import com.proyectointegrador.entity.Usuario;
import com.proyectointegrador.repository.NotificacionRepository;
import com.proyectointegrador.security.UserPrincipal;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Etiqueta QR preventiva: quien encuentra avisa al dueño por el chat sin ver sus datos")
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:foundia_etiquetas_test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.main.banner-mode=off",
        "logging.level.root=WARN"
})
@AutoConfigureMockMvc
@Transactional
class EtiquetaIntegrationTest {

    @Autowired private EntityManager entityManager;
    @Autowired private MockMvc api;
    @Autowired private NotificacionRepository notificaciones;
    private final ObjectMapper json = new ObjectMapper();

    private Usuario ana;
    private Usuario luis;

    @BeforeEach
    void preparar() {
        ana = usuario("Ana");
        luis = usuario("Luis");
        entityManager.flush();
    }

    private Usuario usuario(String nombre) {
        Usuario u = new Usuario();
        u.setNombre(nombre);
        u.setApellido("Apellidoprivado");
        u.setCorreo(UUID.randomUUID() + "@prueba.invalid");
        u.setPassword("no-se-utiliza");
        u.setVerificado(true);
        entityManager.persist(u);
        return u;
    }

    private RequestPostProcessor como(Usuario usuario) {
        UserPrincipal principal = new UserPrincipal(usuario.getId(), usuario.getNombre(), usuario.getCorreo(),
                usuario.getPassword(), Rol.USUARIO);
        return authentication(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    private JsonNode crearEtiqueta(Usuario dueno, String nombre) throws Exception {
        String cuerpo = api.perform(post("/api/etiquetas").with(como(dueno)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"nombre\":\"" + nombre + "\",\"descripcion\":\"Tiene un sticker de gato\"}"))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        return json.readTree(cuerpo);
    }

    private org.springframework.test.web.servlet.ResultActions avisar(String codigo, Usuario quien, String mensaje) throws Exception {
        return api.perform(post("/api/etiquetas/publica/" + codigo + "/avisar").with(como(quien))
                .contentType(MediaType.APPLICATION_JSON).content("{\"mensaje\":\"" + mensaje + "\"}"));
    }

    @Test
    void quienEncuentraEscaneaYEscribeAlDuenoSinVerSusDatos() throws Exception {
        JsonNode etiqueta = crearEtiqueta(ana, "Calculadora Casio");
        String codigo = etiqueta.path("codigo").asText();
        assertThat(codigo).matches("[A-Za-z0-9_-]{22}");

        api.perform(get("/api/etiquetas").with(como(ana)))
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].nombre").value("Calculadora Casio"));

        // Al escanear sin sesión: solo el nombre y la descripción que eligió el dueño
        String publica = api.perform(get("/api/etiquetas/publica/" + codigo))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Calculadora Casio"))
                .andExpect(jsonPath("$.propia").value(false))
                .andReturn().getResponse().getContentAsString();
        assertThat(publica).doesNotContain("Ana", "Apellidoprivado", "prueba.invalid", "usuario");

        String conv = avisar(codigo, luis, "La encontré en el laboratorio 3, la dejo en la biblioteca")
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.tipo").value("ETIQUETA"))
                .andExpect(jsonPath("$.objetoNombre").value("Calculadora Casio"))
                .andExpect(jsonPath("$.objetoId").doesNotExist())
                .andExpect(jsonPath("$.soloLectura").value(false))
                .andReturn().getResponse().getContentAsString();
        long convId = json.readTree(conv).path("id").asLong();

        // La dueña recibe el aviso y ve la conversación con el mensaje
        assertThat(notificaciones.findByUsuarioIdOrderByFechaCreacionDesc(ana.getId()))
                .anySatisfy(n -> assertThat(n.getTitulo()).isEqualTo("Encontraron tu Calculadora Casio"));
        api.perform(get("/api/conversaciones").with(como(ana)))
                .andExpect(jsonPath("$[0].id").value(convId))
                .andExpect(jsonPath("$[0].sinLeer").value(1));
        api.perform(get("/api/conversaciones/" + convId + "/mensajes").with(como(ana)))
                .andExpect(jsonPath("$.mensajes[0].texto").value("La encontré en el laboratorio 3, la dejo en la biblioteca"));

        // Si vuelve a escanear, sigue en la misma conversación
        avisar(codigo, luis, "Ya la dejé").andExpect(jsonPath("$.id").value(convId));
    }

    @Test
    void elDuenoNoSeAvisaASiMismoYAlDesactivarElQrDejaDeFuncionar() throws Exception {
        String codigo = crearEtiqueta(ana, "Casaca azul").path("codigo").asText();
        long id = crearEtiqueta(ana, "Otra").path("id").asLong();

        api.perform(get("/api/etiquetas/publica/" + codigo).with(como(ana))).andExpect(jsonPath("$.propia").value(true));
        avisar(codigo, ana, "Hola").andExpect(status().isBadRequest());
        long convId = json.readTree(avisar(codigo, luis, "La tengo yo").andReturn().getResponse().getContentAsString()).path("id").asLong();

        long idCasaca = json.readTree(api.perform(get("/api/etiquetas").with(como(ana))).andReturn().getResponse().getContentAsString())
                .findValues("id").stream().mapToLong(JsonNode::asLong).filter(x -> x != id).findFirst().orElseThrow();
        api.perform(delete("/api/etiquetas/" + idCasaca).with(como(luis))).andExpect(status().isNotFound());
        api.perform(delete("/api/etiquetas/" + idCasaca).with(como(ana))).andExpect(status().isNoContent());

        api.perform(get("/api/etiquetas/publica/" + codigo)).andExpect(status().isNotFound());
        avisar(codigo, luis, "Hola").andExpect(status().isNotFound());
        api.perform(get("/api/conversaciones/" + convId).with(como(luis))).andExpect(jsonPath("$.soloLectura").value(true));
        api.perform(get("/api/etiquetas").with(como(ana))).andExpect(jsonPath("$.length()").value(1));
    }

    @Test
    void validacionesYLimites() throws Exception {
        String codigo = crearEtiqueta(ana, "USB").path("codigo").asText();
        api.perform(post("/api/etiquetas/publica/" + codigo + "/avisar").contentType(MediaType.APPLICATION_JSON)
                .content("{\"mensaje\":\"hola\"}")).andExpect(status().isUnauthorized());
        api.perform(get("/api/etiquetas/publica/no-existe")).andExpect(status().isNotFound());
        api.perform(get("/api/etiquetas").with(como(ana)).param("x", "1")).andExpect(status().isOk());
        api.perform(post("/api/etiquetas").with(como(ana)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"  \"}")).andExpect(status().isBadRequest());
        api.perform(post("/api/etiquetas").with(como(ana)).contentType(MediaType.APPLICATION_JSON)
                .content("{\"nombre\":\"Mira https://phishing.example\"}")).andExpect(status().isBadRequest());
        avisar(codigo, luis, " ").andExpect(status().isBadRequest());

        // Máximo 5 dueños distintos por día para quien escanea
        for (int i = 0; i < 5; i++) {
            Usuario dueno = usuario("Dueno" + i);
            entityManager.flush();
            avisar(crearEtiqueta(dueno, "Cosa " + i).path("codigo").asText(), luis, "La encontré").andExpect(status().isCreated());
        }
        avisar(codigo, luis, "La encontré").andExpect(status().isTooManyRequests());
    }
}
