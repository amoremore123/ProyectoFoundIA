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
import org.springframework.http.MediaType;
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

@DisplayName("H18 - iniciar contacto privado desde una coincidencia válida")
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:foundia_contacto_test;MODE=MySQL;DB_CLOSE_DELAY=-1",
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
class ContactoIntegrationTest {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private com.proyectointegrador.service.CoincidenciaService motor;

    private Usuario autorPerdido;
    private Usuario autorEncontrado;
    private Usuario otroUsuario;
    private Categoria categoria;
    private Objeto perdido;
    private Objeto encontrado;

    @BeforeEach
    void prepararDatos() {
        autorPerdido = usuario("perdido-contacto@prueba.invalid", "Amalia");
        autorEncontrado = usuario("encontrado-contacto@prueba.invalid", "Bruno");
        otroUsuario = usuario("tercero-contacto@prueba.invalid", "Carla");
        categoria = new Categoria();
        categoria.setNombre("Mochila contacto " + UUID.randomUUID());
        categoria.setEstado(true);
        entityManager.persist(categoria);
        perdido = objeto(autorPerdido, TipoObjeto.PERDIDO, "Mochila extraviada");
        encontrado = objeto(autorEncontrado, TipoObjeto.ENCONTRADO, "Mochila encontrada");
        entityManager.flush();
    }

    private Usuario usuario(String correo, String nombre) {
        Usuario usuario = new Usuario();
        usuario.setNombre(nombre);
        usuario.setApellido("Apellido privado");
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

    private String body(Long origen, Long destino) {
        return """
                {"objetoPerdidoId":%d,"objetoEncontradoId":%d}
                """.formatted(origen, destino);
    }

    @Test
    @DisplayName("Crea contacto y aviso solo al dueño del hallazgo, sin devolver datos privados")
    void iniciarContactoYNotificarDestinatario() throws Exception {
        String respuesta = mockMvc.perform(post("/api/contactos")
                        .with(autenticar(autorPerdido))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(perdido.getId(), encontrado.getId())))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("PENDIENTE"))
                .andExpect(jsonPath("$.coincidenciaId").isNumber())
                .andExpect(jsonPath("$.repetido").value(false))
                .andReturn().getResponse().getContentAsString();
        assertThat(respuesta).doesNotContain(autorPerdido.getCorreo(), autorEncontrado.getCorreo(), "Apellido privado");
        assertThat(entityManager.createQuery("select count(c) from Contacto c", Long.class).getSingleResult())
                .isEqualTo(1L);
        assertThat(entityManager.createQuery("select count(c) from Coincidencia c", Long.class).getSingleResult())
                .isEqualTo(1L);
        assertThat(entityManager.createQuery("select c.porcentaje from Coincidencia c", java.math.BigDecimal.class).getSingleResult())
                .isEqualByComparingTo("73");

        Long notificacionId = ((Number) JsonPath.read(
                mockMvc.perform(get("/api/notificaciones").with(autenticar(autorEncontrado)))
                        .andExpect(status().isOk())
                        .andExpect(jsonPath("$[0].titulo").value("Nueva solicitud de contacto"))
                        .andExpect(jsonPath("$[0].tipo").value("CONTACTO_RECIBIDO"))
                        .andExpect(jsonPath("$[0].leida").value(false))
                        .andReturn().getResponse().getContentAsString(), "$[0].id")).longValue();
        mockMvc.perform(get("/api/notificaciones").with(autenticar(otroUsuario)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));

        mockMvc.perform(put("/api/notificaciones/{id}/leida", notificacionId)
                        .with(autenticar(autorEncontrado)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.leida").value(true));
        entityManager.flush();
        entityManager.clear();
        assertThat(entityManager.createQuery("select n.leida from Notificacion n where n.id = :id", Boolean.class)
                .setParameter("id", notificacionId).getSingleResult()).isTrue();
    }

    @Test
    @DisplayName("Una pareja por debajo del umbral no crea contacto ni aviso")
    void rechazaParejaSinSimilitudSuficiente() throws Exception {
        encontrado.setNombre("Teléfono rojo");
        encontrado.setUbicacion("Comedor");
        encontrado.setFechaObjeto(LocalDate.now().minusDays(60));
        entityManager.flush();
        mockMvc.perform(post("/api/contactos").with(autenticar(autorPerdido))
                        .contentType(MediaType.APPLICATION_JSON).content(body(perdido.getId(), encontrado.getId())))
                .andExpect(status().isBadRequest());
        assertThat(entityManager.createQuery("select count(c) from Contacto c", Long.class).getSingleResult()).isZero();
        assertThat(entityManager.createQuery("select count(n) from Notificacion n", Long.class).getSingleResult()).isZero();
    }

    @Test
    @DisplayName("Una solicitud repetida es idempotente y no duplica contacto ni notificación")
    void contactoRepetidoNoDuplica() throws Exception {
        mockMvc.perform(post("/api/contactos").with(autenticar(autorPerdido))
                        .contentType(MediaType.APPLICATION_JSON).content(body(perdido.getId(), encontrado.getId())))
                .andExpect(status().isCreated());
        mockMvc.perform(post("/api/contactos").with(autenticar(autorPerdido))
                        .contentType(MediaType.APPLICATION_JSON).content(body(perdido.getId(), encontrado.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.repetido").value(true));

        assertThat(entityManager.createQuery("select count(c) from Contacto c", Long.class).getSingleResult())
                .isEqualTo(1L);
        assertThat(entityManager.createQuery("select count(n) from Notificacion n", Long.class).getSingleResult())
                .isEqualTo(1L);
    }

    @Test
    void puntuacionPersistidaCoincideConElMotorSinValoresInventados() throws Exception {
        mockMvc.perform(post("/api/contactos").with(autenticar(autorPerdido))
                        .contentType(MediaType.APPLICATION_JSON).content(body(perdido.getId(), encontrado.getId())))
                .andExpect(status().isCreated());
        var pareja = entityManager.createQuery("select c from Coincidencia c",
                com.proyectointegrador.entity.Coincidencia.class).getSingleResult();
        int puntos = motor.puntuar(perdido, encontrado);
        assertThat(pareja.getPorcentaje()).isEqualByComparingTo(java.math.BigDecimal.valueOf(puntos));
        assertThat(pareja.getNivel().name()).isEqualTo(motor.nivel(puntos));
    }

    @Test
    void noSeContactaUnaParejaQueNoAlcanzaElUmbralDelMotor() throws Exception {
        encontrado.setNombre("Plancha roja");
        encontrado.setUbicacion("Edificio sur");
        encontrado.setFechaObjeto(LocalDate.now().minusDays(80));
        entityManager.flush();
        mockMvc.perform(post("/api/contactos").with(autenticar(autorPerdido))
                        .contentType(MediaType.APPLICATION_JSON).content(body(perdido.getId(), encontrado.getId())))
                .andExpect(status().isBadRequest());
        assertThat(entityManager.createQuery("select count(c) from Contacto c", Long.class).getSingleResult()).isZero();
    }

    @Test
    @DisplayName("Sin sesión, con objeto ajeno o pareja incompatible no se registra contacto")
    void rechazarContactosNoAutorizadosOInvalidos() throws Exception {
        Categoria otraCategoria = new Categoria();
        otraCategoria.setNombre("Otra categoría H18 " + UUID.randomUUID());
        otraCategoria.setEstado(true);
        entityManager.persist(otraCategoria);
        Objeto encontradoNoCoincidente = objeto(autorEncontrado, TipoObjeto.ENCONTRADO, "Otro objeto");
        encontradoNoCoincidente.setCategoria(otraCategoria);
        entityManager.flush();

        mockMvc.perform(post("/api/contactos").contentType(MediaType.APPLICATION_JSON)
                        .content(body(perdido.getId(), encontrado.getId())))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/contactos").with(autenticar(otroUsuario))
                        .contentType(MediaType.APPLICATION_JSON).content(body(perdido.getId(), encontrado.getId())))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/contactos").with(autenticar(autorPerdido))
                        .contentType(MediaType.APPLICATION_JSON).content(body(perdido.getId(), perdido.getId())))
                .andExpect(status().isForbidden());
        mockMvc.perform(post("/api/contactos").with(autenticar(autorPerdido))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(perdido.getId(), encontradoNoCoincidente.getId())))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/contactos").with(autenticar(autorPerdido))
                        .contentType(MediaType.APPLICATION_JSON).content(body(perdido.getId(), 999999L)))
                .andExpect(status().isNotFound());

        assertThat(entityManager.createQuery("select count(c) from Contacto c", Long.class).getSingleResult())
                .isZero();
        assertThat(entityManager.createQuery("select count(n) from Notificacion n", Long.class).getSingleResult())
                .isZero();
    }
}
