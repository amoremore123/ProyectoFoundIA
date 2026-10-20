package com.proyectointegrador.controller;

import com.proyectointegrador.entity.Categoria;
import com.proyectointegrador.entity.EstadoObjeto;
import com.proyectointegrador.entity.Foto;
import com.proyectointegrador.entity.Objeto;
import com.proyectointegrador.entity.TipoObjeto;
import com.proyectointegrador.entity.Usuario;
import com.proyectointegrador.repository.CategoriaRepository;
import com.proyectointegrador.repository.FotoRepository;
import com.proyectointegrador.repository.ObjetoRepository;
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
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsInAnyOrder;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("H14 - Visualizar las publicaciones propias (API + base temporal)")
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:foundia_propias_test;MODE=MySQL;DB_CLOSE_DELAY=-1",
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
class PublicacionesPropiasIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UsuarioRepository usuarioRepository;
    @Autowired private CategoriaRepository categoriaRepository;
    @Autowired private ObjetoRepository objetoRepository;
    @Autowired private FotoRepository fotoRepository;
    @Autowired private JwtService jwtService;
    @MockitoBean private EmailService emailService;

    private Categoria categoria;
    private Objeto mochila;
    private Objeto llaves;
    private Objeto delLuis;
    private String tokenAna;
    private String tokenLuis;
    private String tokenSinPublicaciones;

    @BeforeEach
    void preparar() {
        fotoRepository.deleteAll();
        objetoRepository.deleteAll();
        usuarioRepository.deleteAll();
        categoriaRepository.deleteAll();

        Usuario ana = usuarioRepository.saveAndFlush(usuario("Ana", "ana@prueba.invalid"));
        Usuario luis = usuarioRepository.saveAndFlush(usuario("Luis", "luis@prueba.invalid"));
        Usuario nueva = usuarioRepository.saveAndFlush(usuario("Rosa", "rosa@prueba.invalid"));
        tokenAna = jwtService.generateToken(UserPrincipal.from(ana));
        tokenLuis = jwtService.generateToken(UserPrincipal.from(luis));
        tokenSinPublicaciones = jwtService.generateToken(UserPrincipal.from(nueva));

        categoria = new Categoria();
        categoria.setNombre("Mochilas H14");
        categoria = categoriaRepository.saveAndFlush(categoria);

        mochila = objetoRepository.saveAndFlush(objeto(ana, "Mochila de Ana", EstadoObjeto.ACTIVO));
        llaves = objetoRepository.saveAndFlush(objeto(ana, "Llaves de Ana", EstadoObjeto.OCULTO));
        delLuis = objetoRepository.saveAndFlush(objeto(luis, "Celular de Luis", EstadoObjeto.ACTIVO));

        Foto foto = new Foto();
        foto.setObjeto(mochila);
        foto.setUrl("/uploads/mochila-ana.png");
        foto.setNombreArchivo("mochila-ana.png");
        fotoRepository.saveAndFlush(foto);
    }

    private Usuario usuario(String nombre, String correo) {
        Usuario u = new Usuario();
        u.setNombre(nombre);
        u.setApellido("Prueba");
        u.setCorreo(correo);
        u.setPassword(new BCryptPasswordEncoder(4).encode("Segura123!"));
        u.setVerificado(true);
        return u;
    }

    private Objeto objeto(Usuario autor, String nombre, EstadoObjeto estado) {
        Objeto o = new Objeto();
        o.setUsuario(autor);
        o.setCategoria(categoria);
        o.setNombre(nombre);
        o.setDescripcion("Descripción de " + nombre);
        o.setUbicacion("Biblioteca");
        o.setFechaObjeto(LocalDate.now().minusDays(2));
        o.setTipo(TipoObjeto.PERDIDO);
        o.setEstado(estado);
        return o;
    }

    @Test
    @DisplayName("Cuenta con publicaciones: solo las propias, con foto, estado y datos correctos")
    void soloLasPropiasConFotoYEstado() throws Exception {
        mockMvc.perform(get("/api/perfil/objetos").header("Authorization", "Bearer " + tokenAna))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[*].nombre", containsInAnyOrder("Mochila de Ana", "Llaves de Ana")))
                .andExpect(jsonPath("$[?(@.id == " + mochila.getId() + ")].estado").value("ACTIVO"))
                .andExpect(jsonPath("$[?(@.id == " + mochila.getId() + ")].fotos[0].url").value("/uploads/mochila-ana.png"))
                .andExpect(jsonPath("$[?(@.id == " + llaves.getId() + ")].estado").value("OCULTO"))
                .andExpect(jsonPath("$[?(@.id == " + mochila.getId() + ")].categoria.nombre").value("Mochilas H14"));
    }

    @Test
    @DisplayName("Cuenta sin publicaciones: lista vacía (200), no un error")
    void cuentaSinPublicaciones() throws Exception {
        mockMvc.perform(get("/api/perfil/objetos").header("Authorization", "Bearer " + tokenSinPublicaciones))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @DisplayName("No mezcla resultados de otra sesión")
    void noMezclaOtraSesion() throws Exception {
        String deLuis = mockMvc.perform(get("/api/perfil/objetos").header("Authorization", "Bearer " + tokenLuis))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andReturn().getResponse().getContentAsString();
        assertThat(deLuis).contains("Celular de Luis").doesNotContain("Mochila de Ana", "Llaves de Ana");
    }

    @Test
    @DisplayName("Después de editar, el listado muestra el dato nuevo")
    void reflejaLaEdicion() throws Exception {
        String cuerpo = """
                {"categoriaId":%d,"nombre":"Mochila de Ana editada","descripcion":"Ahora con llavero",
                 "fechaObjeto":"%s","tipo":"PERDIDO","ubicacion":"Cafetería"}
                """.formatted(categoria.getId(), LocalDate.now().minusDays(2));
        mockMvc.perform(put("/api/objetos/" + mochila.getId()).header("Authorization", "Bearer " + tokenAna)
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/perfil/objetos").header("Authorization", "Bearer " + tokenAna))
                .andExpect(jsonPath("$[?(@.id == " + mochila.getId() + ")].nombre").value("Mochila de Ana editada"))
                .andExpect(jsonPath("$[?(@.id == " + mochila.getId() + ")].ubicacion").value("Cafetería"));
    }

    @Test
    @DisplayName("Después de eliminar, sigue en el historial propio como ELIMINADO y desaparece de lo público")
    void reflejaLaEliminacion() throws Exception {
        mockMvc.perform(delete("/api/objetos/" + mochila.getId()).header("Authorization", "Bearer " + tokenAna))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isIn(200, 204));

        mockMvc.perform(get("/api/perfil/objetos").header("Authorization", "Bearer " + tokenAna))
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[?(@.id == " + mochila.getId() + ")].estado").value("ELIMINADO"));
        mockMvc.perform(get("/api/objetos/" + mochila.getId())).andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("Sin token: 401 y sin datos")
    void sinToken() throws Exception {
        String cuerpo = mockMvc.perform(get("/api/perfil/objetos"))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();
        assertThat(cuerpo).doesNotContain("Mochila de Ana");
    }

    @Test
    @DisplayName("No existe una ruta para listar publicaciones ajenas cambiando un id")
    void sinListadoAjenoPorId() throws Exception {
        Long idAna = mochila.getUsuario().getId();
        for (String ruta : new String[] {"/api/usuarios/" + idAna + "/objetos", "/api/perfil/objetos/" + idAna}) {
            var respuesta = mockMvc.perform(get(ruta).header("Authorization", "Bearer " + tokenLuis))
                    .andReturn().getResponse();
            assertThat(respuesta.getStatus()).as(ruta).isGreaterThanOrEqualTo(400);
            assertThat(respuesta.getContentAsString()).as(ruta).doesNotContain("Llaves de Ana", "Mochila de Ana");
        }
    }

    @Test
    @DisplayName("Una publicación OCULTA de otra cuenta no aparece en lo público ni en mis publicaciones")
    void ocultaAjenaNoSeFiltra() throws Exception {
        String publico = mockMvc.perform(get("/api/objetos")).andReturn().getResponse().getContentAsString();
        assertThat(publico).doesNotContain("Llaves de Ana");
        String deLuis = mockMvc.perform(get("/api/perfil/objetos").header("Authorization", "Bearer " + tokenLuis))
                .andReturn().getResponse().getContentAsString();
        assertThat(deLuis).doesNotContain("Llaves de Ana");
        assertThat(delLuis.getId()).isNotNull();
    }
}
