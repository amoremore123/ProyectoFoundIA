package com.proyectointegrador.controller;

import com.jayway.jsonpath.JsonPath;
import com.proyectointegrador.entity.Categoria;
import com.proyectointegrador.entity.EstadoObjeto;
import com.proyectointegrador.entity.Objeto;
import com.proyectointegrador.entity.Rol;
import com.proyectointegrador.entity.TipoObjeto;
import com.proyectointegrador.entity.Usuario;
import com.proyectointegrador.security.UserPrincipal;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("H20 - reclamar una publicación y notificar a su propietario")
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:foundia_reclamos_test;MODE=MySQL;DB_CLOSE_DELAY=-1",
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
class ReclamoIntegrationTest {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private MockMvc mockMvc;

    private Usuario propietario;
    private Usuario reclamante;
    private Usuario tercero;
    private Categoria categoria;
    private Objeto encontrado;
    private Objeto perdido;

    @BeforeEach
    void prepararDatos() {
        propietario = usuario("dueno-reclamo@prueba.invalid", "Bruno");
        reclamante = usuario("reclamante@prueba.invalid", "Amalia");
        tercero = usuario("tercero-reclamo@prueba.invalid", "Carla");
        categoria = new Categoria();
        categoria.setNombre("Categoría H20 " + UUID.randomUUID());
        categoria.setEstado(true);
        entityManager.persist(categoria);
        encontrado = objeto(propietario, TipoObjeto.ENCONTRADO, "Mochila hallada");
        perdido = objeto(reclamante, TipoObjeto.PERDIDO, "Mochila extraviada");
        entityManager.flush();
    }

    private Usuario usuario(String correo, String nombre) {
        Usuario usuario = new Usuario();
        usuario.setNombre(nombre);
        usuario.setApellido("Prueba");
        usuario.setCorreo(correo);
        usuario.setPassword("no-se-utiliza");
        usuario.setVerificado(true);
        entityManager.persist(usuario);
        return usuario;
    }

    private Objeto objeto(Usuario usuario, TipoObjeto tipo, String nombre) {
        Objeto objeto = new Objeto();
        objeto.setUsuario(usuario);
        objeto.setCategoria(categoria);
        objeto.setNombre(nombre);
        objeto.setDescripcion("Descripción de prueba");
        objeto.setUbicacion("Biblioteca");
        objeto.setFechaObjeto(LocalDate.now().minusDays(1));
        objeto.setTipo(tipo);
        objeto.setEstado(EstadoObjeto.ACTIVO);
        entityManager.persist(objeto);
        return objeto;
    }

    private RequestPostProcessor autenticar(Usuario usuario) {
        UserPrincipal principal = new UserPrincipal(usuario.getId(), usuario.getNombre(), usuario.getCorreo(),
                usuario.getPassword(), Rol.USUARIO);
        return authentication(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    @Test
    @DisplayName("Reclamo válido notifica al dueño y el receptor puede leer su aviso")
    void reclamaYNotificaAlPropietario() throws Exception {
        String respuesta = mockMvc.perform(post("/api/objetos/{id}/reclamos", encontrado.getId())
                        .with(autenticar(reclamante)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.objetoId").value(encontrado.getId()))
                .andExpect(jsonPath("$.estado").value("PENDIENTE"))
                .andExpect(jsonPath("$.repetido").value(false))
                .andReturn().getResponse().getContentAsString();
        assertThat(respuesta).doesNotContain(propietario.getCorreo(), reclamante.getCorreo(), "Apellido completo");
        assertThat(entityManager.createQuery("select count(r) from Reclamo r", Long.class).getSingleResult())
                .isEqualTo(1L);

        String notificaciones = mockMvc.perform(get("/api/notificaciones").with(autenticar(propietario)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].titulo").value("Nuevo reclamo de objeto"))
                .andExpect(jsonPath("$[0].tipo").value("RECLAMO_RECIBIDO"))
                .andExpect(jsonPath("$[0].leida").value(false))
                .andReturn().getResponse().getContentAsString();
        Long notificacionId = ((Number) JsonPath.read(notificaciones, "$[0].id")).longValue();

        mockMvc.perform(get("/api/notificaciones").with(autenticar(tercero)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(put("/api/notificaciones/{id}/leida", notificacionId).with(autenticar(tercero)))
                .andExpect(status().isNotFound());
        mockMvc.perform(put("/api/notificaciones/{id}/leida", notificacionId).with(autenticar(propietario)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.leida").value(true));

        entityManager.flush();
        entityManager.clear();
        assertThat(entityManager.createQuery("select n.leida from Notificacion n where n.id = :id", Boolean.class)
                .setParameter("id", notificacionId).getSingleResult()).isTrue();
    }

    @Test
    @DisplayName("Repetir un reclamo no duplica ni la fila ni la notificación")
    void reclamoRepetidoEsIdempotente() throws Exception {
        mockMvc.perform(post("/api/objetos/{id}/reclamos", encontrado.getId()).with(autenticar(reclamante)))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/objetos/{id}/reclamos", encontrado.getId()).with(autenticar(reclamante)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.repetido").value(true));

        assertThat(entityManager.createQuery("select count(r) from Reclamo r", Long.class).getSingleResult())
                .isEqualTo(1L);
        assertThat(entityManager.createQuery("select count(n) from Notificacion n", Long.class).getSingleResult())
                .isEqualTo(1L);
    }

    @Test
    @DisplayName("Reclamo sin sesión, propio, sobre objeto perdido o inexistente no crea avisos")
    void reclamosSinAutorizacionOInvalidosNoNotifican() throws Exception {
        mockMvc.perform(post("/api/objetos/{id}/reclamos", encontrado.getId()))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/objetos/{id}/reclamos", encontrado.getId()).with(autenticar(propietario)))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/objetos/{id}/reclamos", perdido.getId()).with(autenticar(tercero)))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/objetos/{id}/reclamos", 999999L).with(autenticar(reclamante)))
                .andExpect(status().isNotFound());

        assertThat(entityManager.createQuery("select count(r) from Reclamo r", Long.class).getSingleResult())
                .isZero();
        assertThat(entityManager.createQuery("select count(n) from Notificacion n", Long.class).getSingleResult())
                .isZero();
    }
}
