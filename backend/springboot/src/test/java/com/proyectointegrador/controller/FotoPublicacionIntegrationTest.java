package com.proyectointegrador.controller;

import com.proyectointegrador.entity.Categoria;
import com.proyectointegrador.entity.Objeto;
import com.proyectointegrador.entity.Rol;
import com.proyectointegrador.entity.TipoObjeto;
import com.proyectointegrador.entity.Usuario;
import com.proyectointegrador.security.UserPrincipal;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("H2 - carga y visualización segura de fotos de publicación")
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:foundia_fotos_test;MODE=MySQL;DB_CLOSE_DELAY=-1",
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
class FotoPublicacionIntegrationTest {

    private static final long TAMANO_MAXIMO = 5L * 1024 * 1024;
    private static final Path DIRECTORIO_IMAGENES = Path.of(System.getProperty("java.io.tmpdir"),
            "foundia-fotos-" + UUID.randomUUID());

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registry) {
        registry.add("app.upload.directory", DIRECTORIO_IMAGENES::toString);
    }

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private MockMvc mockMvc;

    private Usuario autor;
    private Usuario otroUsuario;
    private Long objetoId;

    @BeforeEach
    void prepararDatos() {
        autor = usuario("autor-foto@prueba.invalid", "Ana");
        otroUsuario = usuario("otro-foto@prueba.invalid", "Luis");
        Categoria categoria = new Categoria();
        categoria.setNombre("Prueba fotos " + UUID.randomUUID());
        categoria.setEstado(true);
        entityManager.persist(categoria);

        Objeto objeto = new Objeto();
        objeto.setUsuario(autor);
        objeto.setCategoria(categoria);
        objeto.setNombre("Mochila azul");
        objeto.setDescripcion("Descripción de prueba");
        objeto.setUbicacion("Biblioteca");
        objeto.setFechaObjeto(LocalDate.now());
        objeto.setTipo(TipoObjeto.PERDIDO);
        entityManager.persist(objeto);
        entityManager.flush();
        objetoId = objeto.getId();
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

    private RequestPostProcessor autenticar(Usuario usuario) {
        UserPrincipal principal = new UserPrincipal(usuario.getId(), usuario.getNombre(), usuario.getCorreo(),
                usuario.getPassword(), Rol.USUARIO);
        return authentication(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    @Test
    @DisplayName("El autor carga una imagen válida de 5 MiB, se asocia y se visualiza desde la URL pública")
    void cargaImagenEnLimiteYLaVisualiza() throws Exception {
        byte[] png = imagenPngDeTamano(TAMANO_MAXIMO);
        MvcResult resultado = mockMvc.perform(multipart("/api/objetos/{id}/fotos", objetoId)
                        .file(new MockMultipartFile("archivo", "foto.png", MediaType.IMAGE_PNG_VALUE, png))
                        .with(autenticar(autor)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.url").isString())
                .andReturn();

        String url = com.jayway.jsonpath.JsonPath.read(resultado.getResponse().getContentAsString(), "$.url");
        assertThat(url).startsWith("/uploads/");
        try (var archivos = Files.list(DIRECTORIO_IMAGENES)) {
            assertThat(archivos.count()).isEqualTo(1L);
        }

        mockMvc.perform(get("/api/objetos/{id}", objetoId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fotos[0].url").value(url));

        byte[] visualizada = mockMvc.perform(get(url))
                .andExpect(status().isOk())
                .andExpect(org.springframework.test.web.servlet.result.MockMvcResultMatchers.content()
                        .contentTypeCompatibleWith(MediaType.IMAGE_PNG))
                .andReturn().getResponse().getContentAsByteArray();
        assertThat(visualizada).isEqualTo(png);
        assertThat(entityManager.createQuery("select count(f) from Foto f", Long.class).getSingleResult()).isEqualTo(1L);
    }

    @Test
    @DisplayName("Formato inválido y tamaño superior se rechazan sin asociación ni archivo")
    void rechazaArchivoInvalidoYExcesoSinHuerfanos() throws Exception {
        byte[] invalida = "no es una imagen".getBytes(java.nio.charset.StandardCharsets.UTF_8);
        mockMvc.perform(multipart("/api/objetos/{id}/fotos", objetoId)
                        .file(new MockMultipartFile("archivo", "foto.png", MediaType.IMAGE_PNG_VALUE, invalida))
                        .with(autenticar(autor)))
                .andExpect(status().isBadRequest());

        byte[] excesiva = imagenPngDeTamano(TAMANO_MAXIMO + 1);
        mockMvc.perform(multipart("/api/objetos/{id}/fotos", objetoId)
                        .file(new MockMultipartFile("archivo", "foto.png", MediaType.IMAGE_PNG_VALUE, excesiva))
                        .with(autenticar(autor)))
                .andExpect(status().isBadRequest());

        assertThat(entityManager.createQuery("select count(f) from Foto f", Long.class).getSingleResult()).isZero();
        assertThat(Files.exists(DIRECTORIO_IMAGENES)).isFalse();
    }

    @Test
    @DisplayName("Solo el autor autenticado puede añadir una foto")
    void soloAutorPuedeSubirFoto() throws Exception {
        byte[] png = imagenPngDeTamano(100);

        mockMvc.perform(multipart("/api/objetos/{id}/fotos", objetoId)
                        .file(new MockMultipartFile("archivo", "foto.png", MediaType.IMAGE_PNG_VALUE, png))
                        .with(autenticar(otroUsuario)))
                .andExpect(status().isForbidden());

        mockMvc.perform(multipart("/api/objetos/{id}/fotos", objetoId)
                        .file(new MockMultipartFile("archivo", "foto.png", MediaType.IMAGE_PNG_VALUE, png)))
                .andExpect(status().isUnauthorized());

        assertThat(entityManager.createQuery("select count(f) from Foto f", Long.class).getSingleResult()).isZero();
        assertThat(Files.exists(DIRECTORIO_IMAGENES)).isFalse();
    }

    private byte[] imagenPngDeTamano(long tamano) throws IOException {
        BufferedImage imagen = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        ImageIO.write(imagen, "png", salida);
        byte[] base = salida.toByteArray();
        if (tamano < base.length) {
            throw new IllegalArgumentException("El tamaño solicitado es menor que una imagen PNG válida");
        }
        return java.util.Arrays.copyOf(base, Math.toIntExact(tamano));
    }

    @AfterEach
    void borrarArchivosTemporales() throws IOException {
        if (Files.exists(DIRECTORIO_IMAGENES)) {
            try (var paths = Files.walk(DIRECTORIO_IMAGENES)) {
                paths.sorted(Comparator.reverseOrder()).forEach(ruta -> {
                    try {
                        Files.deleteIfExists(ruta);
                    } catch (IOException ex) {
                        throw new IllegalStateException(ex);
                    }
                });
            }
        }
    }
}
