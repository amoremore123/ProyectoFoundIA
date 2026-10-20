package com.proyectointegrador.controller;

import com.proyectointegrador.entity.Categoria;
import com.proyectointegrador.entity.EstadoObjeto;
import com.proyectointegrador.entity.Foto;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("H5 - editar y eliminar publicaciones propias")
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:foundia_edicion_test;MODE=MySQL;DB_CLOSE_DELAY=-1",
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
class EdicionPublicacionIntegrationTest {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private MockMvc mockMvc;

    private Usuario autor;
    private Usuario otroUsuario;
    private Usuario administrador;
    private Categoria categoria;
    private Objeto objeto;

    @BeforeEach
    void prepararDatos() {
        autor = usuario("autor-h5@prueba.invalid", "Ana", Rol.USUARIO);
        otroUsuario = usuario("otro-h5@prueba.invalid", "Luis", Rol.USUARIO);
        administrador = usuario("admin-h5@prueba.invalid", "Admin", Rol.ADMIN);
        categoria = new Categoria();
        categoria.setNombre("Categoría H5 " + UUID.randomUUID());
        categoria.setEstado(true);
        entityManager.persist(categoria);

        objeto = new Objeto();
        objeto.setUsuario(autor);
        objeto.setCategoria(categoria);
        objeto.setNombre("Mochila original");
        objeto.setDescripcion("Descripción original");
        objeto.setUbicacion("Biblioteca antigua");
        objeto.setFechaObjeto(LocalDate.now().minusDays(1));
        objeto.setTipo(TipoObjeto.PERDIDO);
        entityManager.persist(objeto);

        Foto foto = new Foto();
        foto.setObjeto(objeto);
        foto.setUrl("/uploads/foto-conservada.png");
        foto.setNombreArchivo("foto-conservada.png");
        entityManager.persist(foto);
        entityManager.flush();
    }

    private Usuario usuario(String correo, String nombre, Rol rol) {
        Usuario usuario = new Usuario();
        usuario.setNombre(nombre);
        usuario.setApellido("Prueba");
        usuario.setCorreo(correo);
        usuario.setPassword("no-se-utiliza");
        usuario.setRol(rol);
        usuario.setVerificado(true);
        entityManager.persist(usuario);
        return usuario;
    }

    private RequestPostProcessor autenticar(Usuario usuario) {
        UserPrincipal principal = new UserPrincipal(usuario.getId(), usuario.getNombre(), usuario.getCorreo(),
                usuario.getPassword(), usuario.getRol());
        return authentication(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    private String body(String nombre, String descripcion, String ubicacion, String fecha) {
        return """
                {"categoriaId":%d,"nombre":"%s","descripcion":"%s","fechaObjeto":"%s",
                 "tipo":"ENCONTRADO","ubicacion":"%s","latitud":null,"longitud":null}
                """.formatted(categoria.getId(), nombre, descripcion, fecha, ubicacion);
    }

    @Test
    @DisplayName("El autor edita y los cambios aparecen en la respuesta, la base, el detalle y la búsqueda")
    void autorActualizaPublicacion() throws Exception {
        String fecha = LocalDate.now().minusDays(2).toString();
        mockMvc.perform(put("/api/objetos/{id}", objeto.getId())
                        .with(autenticar(autor))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Cédula nueva", "Cédula, cómputo y ñ", "Laboratorio de cómputo", fecha)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value("Cédula nueva"))
                .andExpect(jsonPath("$.descripcion").value("Cédula, cómputo y ñ"))
                .andExpect(jsonPath("$.ubicacion").value("Laboratorio de cómputo"))
                .andExpect(jsonPath("$.fechaObjeto").value(fecha));

        entityManager.flush();
        entityManager.clear();
        Object nombreEnBase = entityManager.createNativeQuery("select nombre from objetos where id = :id")
                .setParameter("id", objeto.getId()).getSingleResult();
        assertThat(nombreEnBase).isEqualTo("Cédula nueva");

        mockMvc.perform(get("/api/objetos/{id}", objeto.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.ubicacion").value("Laboratorio de cómputo"));
        mockMvc.perform(get("/api/objetos/buscar").param("q", "Cédula nueva"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(objeto.getId()));
    }

    @Test
    @DisplayName("PUT conserva validaciones de nombre, descripción, ubicación y fecha antes de guardar")
    void putRechazaDatosInvalidosSinCambiarLaPublicacion() throws Exception {
        String fecha = LocalDate.now().minusDays(1).toString();
        String[][] solicitudesInvalidas = {
                {"N".repeat(151), "Descripción válida", "Ubicación", fecha},
                {"Nombre", "D".repeat(16001), "Ubicación", fecha},
                {"Nombre", "Descripción válida", "U".repeat(256), fecha},
                {"Nombre", "Descripción válida", "Ubicación", LocalDate.now().plusDays(1).toString()}
        };

        for (String[] datos : solicitudesInvalidas) {
            mockMvc.perform(put("/api/objetos/{id}", objeto.getId())
                            .with(autenticar(autor))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body(datos[0], datos[1], datos[2], datos[3])))
                    .andExpect(status().isBadRequest());
        }
        entityManager.flush();
        entityManager.clear();
        assertThat(entityManager.createNativeQuery("select nombre from objetos where id = :id")
                .setParameter("id", objeto.getId()).getSingleResult()).isEqualTo("Mochila original");
    }

    @Test
    @DisplayName("Otro usuario, ADMIN y visitante no pueden editar ni borrar")
    void soloAutorAdministraPublicacion() throws Exception {
        for (Usuario usuarioNoAutor : new Usuario[]{otroUsuario, administrador}) {
            mockMvc.perform(put("/api/objetos/{id}", objeto.getId())
                            .with(autenticar(usuarioNoAutor))
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body("Alterada", "Descripción", "Ubicación", LocalDate.now().toString())))
                    .andExpect(status().isForbidden());
            mockMvc.perform(delete("/api/objetos/{id}", objeto.getId()).with(autenticar(usuarioNoAutor)))
                    .andExpect(status().isForbidden());
        }

        mockMvc.perform(put("/api/objetos/{id}", objeto.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body("Visitante", "Descripción", "Ubicación", LocalDate.now().toString())))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(delete("/api/objetos/{id}", objeto.getId())).andExpect(status().isUnauthorized());

        entityManager.flush();
        entityManager.clear();
        assertThat(entityManager.createNativeQuery("select nombre from objetos where id = :id")
                .setParameter("id", objeto.getId()).getSingleResult()).isEqualTo("Mochila original");
        assertThat(entityManager.createNativeQuery("select estado from objetos where id = :id")
                .setParameter("id", objeto.getId()).getSingleResult()).isEqualTo("ACTIVO");
    }

    @Test
    @DisplayName("DELETE del autor conserva el objeto y las fotos pero lo oculta del público")
    void eliminaLogicamenteSinRomperAsociaciones() throws Exception {
        Long id = objeto.getId();
        mockMvc.perform(delete("/api/objetos/{id}", id).with(autenticar(autor)))
                .andExpect(status().isNoContent());

        entityManager.flush();
        entityManager.clear();
        assertThat(entityManager.createNativeQuery("select estado from objetos where id = :id")
                .setParameter("id", id).getSingleResult()).isEqualTo("ELIMINADO");
        assertThat(entityManager.createQuery("select count(o) from Objeto o where o.id = :id", Long.class)
                .setParameter("id", id).getSingleResult()).isEqualTo(1L);
        assertThat(entityManager.createQuery("select count(f) from Foto f where f.objeto.id = :id", Long.class)
                .setParameter("id", id).getSingleResult()).isEqualTo(1L);

        mockMvc.perform(get("/api/objetos/{id}", id)).andExpect(status().isNotFound());
        mockMvc.perform(get("/api/objetos/buscar").param("q", "Mochila original"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/perfil/objetos").with(autenticar(autor)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].estado").value("ELIMINADO"));

        mockMvc.perform(delete("/api/objetos/{id}", id).with(autenticar(autor)))
                .andExpect(status().isNotFound());
    }
}
