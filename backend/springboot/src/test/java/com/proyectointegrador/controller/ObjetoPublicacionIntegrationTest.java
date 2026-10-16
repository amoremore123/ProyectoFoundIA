package com.proyectointegrador.controller;

import com.proyectointegrador.entity.Categoria;
import com.proyectointegrador.entity.Usuario;
import com.proyectointegrador.entity.Rol;
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
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("H3 - Publicación persiste ubicación y coordenadas en una base temporal")
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:foundia_publicacion_test;MODE=MySQL;DB_CLOSE_DELAY=-1",
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
class ObjetoPublicacionIntegrationTest {

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private MockMvc mockMvc;

    private Usuario usuario;
    private Categoria categoria;
    private UsernamePasswordAuthenticationToken auth;

    @BeforeEach
    void prepararDatos() {
        usuario = new Usuario();
        usuario.setNombre("Amore");
        usuario.setApellido("Prueba");
        usuario.setCorreo("h3-publicacion@prueba.invalid");
        usuario.setPassword("no-se-utiliza");
        usuario.setVerificado(true);
        entityManager.persist(usuario);

        categoria = new Categoria();
        categoria.setNombre("Documentos de prueba");
        categoria.setEstado(true);
        entityManager.persist(categoria);
        entityManager.flush();

        UserPrincipal principal = new UserPrincipal(usuario.getId(), usuario.getNombre(), usuario.getCorreo(),
                usuario.getPassword(), Rol.USUARIO);
        auth = new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities());
    }

    @Test
    @DisplayName("POST, base de datos y detalle conservan exactamente tildes, ubicación y coordenadas")
    void publicaYConsultaDatosSinAlterarlos() throws Exception {
        String nombre = "Cédula extraviada";
        String descripcion = "Cédula, cómputo, ñ y emoji 😃";
        String ubicacion = "Laboratorio de cómputo, edificio ñ";
        LocalDate fechaObjeto = LocalDate.now().minusDays(1);
        BigDecimal latitud = new BigDecimal("19.1234567");
        BigDecimal longitud = new BigDecimal("-99.7654321");
        String body = """
                {"categoriaId":%d,"nombre":"%s","descripcion":"%s","fechaObjeto":"%s",
                 "tipo":"PERDIDO","ubicacion":"%s","latitud":%s,"longitud":%s}
                """.formatted(categoria.getId(), nombre, descripcion, fechaObjeto, ubicacion, latitud, longitud);

        String response = mockMvc.perform(post("/api/objetos")
                        .with(authentication(auth))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nombre").value(nombre))
                .andExpect(jsonPath("$.descripcion").value(descripcion))
                .andExpect(jsonPath("$.ubicacion").value(ubicacion))
                .andExpect(jsonPath("$.fechaObjeto").value(fechaObjeto.toString()))
                .andExpect(jsonPath("$.latitud").value(latitud.doubleValue()))
                .andExpect(jsonPath("$.longitud").value(longitud.doubleValue()))
                .andReturn().getResponse().getContentAsString();

        Long id = ((Number) com.jayway.jsonpath.JsonPath.read(response, "$.id")).longValue();
        entityManager.flush();
        entityManager.clear();

        Object ubicacionDb = entityManager.createNativeQuery("select ubicacion from objetos where id = :id")
                .setParameter("id", id).getSingleResult();
        Object latitudDb = entityManager.createNativeQuery("select latitud from objetos where id = :id")
                .setParameter("id", id).getSingleResult();
        Object longitudDb = entityManager.createNativeQuery("select longitud from objetos where id = :id")
                .setParameter("id", id).getSingleResult();
        Object fechaDb = entityManager.createNativeQuery("select fecha_objeto from objetos where id = :id")
                .setParameter("id", id).getSingleResult();
        assertThat(ubicacionDb).isEqualTo(ubicacion);
        assertThat(new BigDecimal(latitudDb.toString())).isEqualByComparingTo(latitud);
        assertThat(new BigDecimal(longitudDb.toString())).isEqualByComparingTo(longitud);
        assertThat(((java.sql.Date) fechaDb).toLocalDate()).isEqualTo(fechaObjeto);

        mockMvc.perform(get("/api/objetos/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nombre").value(nombre))
                .andExpect(jsonPath("$.descripcion").value(descripcion))
                .andExpect(jsonPath("$.ubicacion").value(ubicacion))
                .andExpect(jsonPath("$.fechaObjeto").value(fechaObjeto.toString()))
                .andExpect(jsonPath("$.latitud").value(latitud.doubleValue()))
                .andExpect(jsonPath("$.longitud").value(longitud.doubleValue()));
    }
}
