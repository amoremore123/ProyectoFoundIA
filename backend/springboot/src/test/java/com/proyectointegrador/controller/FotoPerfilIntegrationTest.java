package com.proyectointegrador.controller;

import com.jayway.jsonpath.JsonPath;
import com.proyectointegrador.entity.TipoObjeto;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// H64: foto de perfil privada, solo para la propia cuenta
@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:foto_perfil_h64;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.main.banner-mode=off", "logging.level.root=WARN"})
@AutoConfigureMockMvc
class FotoPerfilIntegrationTest extends Sprint2Fixtures {

    static final String RUTA = "/api/perfil/foto";
    static final Path DIRECTORIO = Path.of(System.getProperty("java.io.tmpdir"), "foundia-perfil-" + UUID.randomUUID());
    static final Path PERFILES = DIRECTORIO.resolve("perfiles");

    @DynamicPropertySource
    static void propiedades(DynamicPropertyRegistry registry) {
        registry.add("app.upload.directory", DIRECTORIO::toString);
    }

    // Cada prueba cuenta sus propios archivos: la limpieza de la base no pasa por el servicio
    @org.junit.jupiter.api.BeforeEach
    void vaciarPerfiles() throws Exception {
        if (Files.exists(PERFILES)) {
            try (Stream<Path> s = Files.list(PERFILES)) {
                s.forEach(p -> p.toFile().delete());
            }
        }
    }

    @AfterAll
    static void borrarDirectorio() throws Exception {
        if (Files.exists(DIRECTORIO)) {
            try (Stream<Path> rutas = Files.walk(DIRECTORIO)) {
                rutas.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
            }
        }
    }

    static byte[] imagen(String formato, int lado) throws Exception {
        BufferedImage img = new BufferedImage(lado, lado, BufferedImage.TYPE_INT_RGB);
        img.setRGB(0, 0, 0x336699);
        ByteArrayOutputStream salida = new ByteArrayOutputStream();
        ImageIO.write(img, formato, salida);
        return salida.toByteArray();
    }

    ResultActions subir(RequestPostProcessor quien, String nombre, String tipo, byte[] datos) throws Exception {
        return api.perform(multipart(RUTA).file(new MockMultipartFile("archivo", nombre, tipo, datos)).with(quien));
    }

    long archivosEnPerfiles() throws Exception {
        if (!Files.exists(PERFILES)) return 0;
        try (Stream<Path> s = Files.list(PERFILES)) { return s.count(); }
    }

    String rutaGuardada(Long usuarioId) {
        return jdbc.queryForObject("SELECT foto_perfil_url FROM usuarios WHERE id = ?", String.class, usuarioId);
    }

    @Test
    void sinSesionResponde401() throws Exception {
        api.perform(get(RUTA)).andExpect(status().isUnauthorized());
        api.perform(delete(RUTA)).andExpect(status().isUnauthorized());
        api.perform(multipart(RUTA).file(new MockMultipartFile("archivo", "a.png", "image/png", imagen("png", 4))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void subirLaFotoYVerlaSoloConLaPropiaSesion() throws Exception {
        byte[] png = imagen("png", 8);
        String url = JsonPath.read(subir(como(ana), "yo.png", "image/png", png)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fotoPerfilUrl").value(containsString("/api/perfil/foto?v=")))
                .andReturn().getResponse().getContentAsString(), "$.fotoPerfilUrl");

        api.perform(get(RUTA).with(como(ana))).andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.IMAGE_PNG))
                .andExpect(header().string("Cache-Control", containsString("private")))
                .andExpect(content().bytes(png));
        api.perform(get("/api/perfil").with(como(ana))).andExpect(jsonPath("$.fotoPerfilUrl").value(url));
        assertThat(rutaGuardada(ana.getId())).matches("perfiles/[0-9a-f-]{36}\\.png");
        assertThat(archivosEnPerfiles()).isEqualTo(1);

        // Otra cuenta no obtiene la foto de Ana: solo la propia, que no tiene
        api.perform(get(RUTA).with(como(luis))).andExpect(status().isNotFound());
        api.perform(get("/api/perfil").with(como(luis))).andExpect(jsonPath("$.fotoPerfilUrl").doesNotExist());
    }

    @Test
    void reemplazarBorraElArchivoAnteriorYCambiaLaVersion() throws Exception {
        String primera = JsonPath.read(subir(como(ana), "a.png", "image/png", imagen("png", 6))
                .andReturn().getResponse().getContentAsString(), "$.fotoPerfilUrl");
        Path archivoAnterior = DIRECTORIO.resolve(rutaGuardada(ana.getId()));
        assertThat(archivoAnterior).exists();

        byte[] jpg = imagen("jpg", 10);
        String segunda = JsonPath.read(subir(como(ana), "b.jpg", "image/jpeg", jpg)
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString(), "$.fotoPerfilUrl");

        assertThat(segunda).isNotEqualTo(primera);
        assertThat(archivoAnterior).doesNotExist();
        assertThat(archivosEnPerfiles()).isEqualTo(1);
        api.perform(get(RUTA).with(como(ana))).andExpect(content().contentType(MediaType.IMAGE_JPEG))
                .andExpect(content().bytes(jpg));
    }

    @Test
    void borrarQuitaLaFotoYElArchivoYEsRepetible() throws Exception {
        subir(como(ana), "a.png", "image/png", imagen("png", 6)).andExpect(status().isOk());
        Path archivo = DIRECTORIO.resolve(rutaGuardada(ana.getId()));

        api.perform(delete(RUTA).with(como(ana))).andExpect(status().isNoContent());
        assertThat(archivo).doesNotExist();
        assertThat(rutaGuardada(ana.getId())).isNull();
        api.perform(get(RUTA).with(como(ana))).andExpect(status().isNotFound());
        api.perform(get("/api/perfil").with(como(ana))).andExpect(jsonPath("$.fotoPerfilUrl").doesNotExist());
        api.perform(delete(RUTA).with(como(ana))).andExpect(status().isNoContent());
    }

    @Test
    void archivosInvalidosResponden400SinTocarLaFotoActual() throws Exception {
        byte[] png = imagen("png", 6);
        subir(como(ana), "a.png", "image/png", png).andExpect(status().isOk());
        String actual = rutaGuardada(ana.getId());

        subir(como(ana), "nota.txt", "text/plain", "hola".getBytes()).andExpect(status().isBadRequest());
        subir(como(ana), "falsa.png", "image/png", "no soy una imagen".getBytes()).andExpect(status().isBadRequest());
        subir(como(ana), "vacia.png", "image/png", new byte[0]).andExpect(status().isBadRequest());
        subir(como(ana), "grande.png", "image/png", new byte[5 * 1024 * 1024 + 1]).andExpect(status().isBadRequest());
        api.perform(multipart(RUTA).with(como(ana))).andExpect(status().isBadRequest());

        assertThat(rutaGuardada(ana.getId())).isEqualTo(actual);
        assertThat(archivosEnPerfiles()).isEqualTo(1);
        api.perform(get(RUTA).with(como(ana))).andExpect(content().bytes(png));
    }

    @Test
    void laRutaPublicaDeLasFotosDePerfilEstaBloqueada() throws Exception {
        subir(como(ana), "a.png", "image/png", imagen("png", 6)).andExpect(status().isOk());
        String publica = "/uploads/" + rutaGuardada(ana.getId());

        int sinSesion = api.perform(get(publica)).andReturn().getResponse().getStatus();
        int conSesion = api.perform(get(publica).with(como(ana))).andReturn().getResponse().getStatus();
        assertThat(sinSesion).isIn(401, 403);
        assertThat(conSesion).isEqualTo(403);
    }

    @Test
    void laFotoNoApareceEnLasPublicaciones() throws Exception {
        subir(como(ana), "a.png", "image/png", imagen("png", 6)).andExpect(status().isOk());
        var publicacion = objeto(ana, TipoObjeto.PERDIDO);
        api.perform(get("/api/objetos/" + publicacion.getId())).andExpect(status().isOk())
                .andExpect(content().string(not(containsString("perfil"))));
    }
}
