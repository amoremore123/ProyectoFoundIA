package com.proyectointegrador.controller;

import com.proyectointegrador.entity.EstadoUsuario;
import com.proyectointegrador.entity.Rol;
import com.proyectointegrador.entity.Usuario;
import com.proyectointegrador.repository.SolicitudEliminacionRepository;
import com.proyectointegrador.repository.UsuarioRepository;
import com.proyectointegrador.service.EliminacionCuentaService;
import com.proyectointegrador.service.LimpiadorArchivosEliminados;
import com.proyectointegrador.security.UserPrincipal;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import java.util.UUID;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// H34: la parte de Spring de la eliminación real (Django anonimiza; Spring borra las fotos y respeta ELIMINADO)
@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:eliminacion_h34;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa", "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop", "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.main.banner-mode=off", "logging.level.root=WARN", "app.eliminacion.retraso-inicial-ms=3600000"})
@AutoConfigureMockMvc
class EliminacionCuentaIntegrationTest {

    static final Path FOTOS = Path.of(System.getProperty("java.io.tmpdir"), "foundia-h34-" + UUID.randomUUID());
    static final String PASSWORD = "Segura123!";

    @DynamicPropertySource
    static void carpeta(DynamicPropertyRegistry registro) {
        registro.add("app.upload.directory", FOTOS::toString);
    }

    @AfterAll
    static void borrarCarpeta() throws Exception {
        if (Files.exists(FOTOS.getParent().resolve(FOTOS.getFileName() + "-afuera.txt"))) {
            Files.delete(FOTOS.getParent().resolve(FOTOS.getFileName() + "-afuera.txt"));
        }
        if (Files.exists(FOTOS)) {
            try (Stream<Path> rutas = Files.walk(FOTOS)) {
                rutas.sorted(Comparator.reverseOrder()).forEach(p -> p.toFile().delete());
            }
        }
    }

    @Autowired MockMvc api;
    @Autowired JdbcTemplate jdbc;
    @Autowired UsuarioRepository usuarios;
    @Autowired SolicitudEliminacionRepository solicitudes;
    @Autowired LimpiadorArchivosEliminados limpiador;

    @BeforeEach
    void preparar() {
        jdbc.execute("CREATE TABLE IF NOT EXISTS archivos_por_borrar (id BIGINT AUTO_INCREMENT PRIMARY KEY, "
                + "ruta VARCHAR(255) NOT NULL, anotado_en DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP)");
        jdbc.update("DELETE FROM archivos_por_borrar");
        solicitudes.deleteAll();
        usuarios.deleteAll();
    }

    Path archivo(String ruta) throws Exception {
        Path p = FOTOS.resolve(ruta);
        Files.createDirectories(p.getParent());
        Files.write(p, new byte[]{1, 2, 3});
        return p;
    }

    void anotar(String ruta) {
        jdbc.update("INSERT INTO archivos_por_borrar (ruta) VALUES (?)", ruta);
    }

    int anotadas() {
        return jdbc.queryForObject("SELECT COUNT(*) FROM archivos_por_borrar", Integer.class);
    }

    Usuario usuario(String correo, EstadoUsuario estado) {
        Usuario u = new Usuario();
        u.setNombre("Rosa");
        u.setApellido("Prueba");
        u.setCorreo(correo);
        u.setPassword(new BCryptPasswordEncoder(4).encode(PASSWORD));
        u.setRol(Rol.USUARIO);
        u.setVerificado(true);
        u.setEstado(estado);
        return usuarios.saveAndFlush(u);
    }

    @Test
    @DisplayName("Borra las fotos anotadas (perfil, chat y publicación) y vacía la tabla")
    void borraLasFotosAnotadas() throws Exception {
        String perfil = "perfiles/" + UUID.randomUUID() + ".jpg";
        String chat = "chat/" + UUID.randomUUID() + ".png";
        String publicacion = UUID.randomUUID() + ".jpg";
        Path a = archivo(perfil), b = archivo(chat), c = archivo(publicacion);
        Path otraPublicacion = archivo(UUID.randomUUID() + ".jpg");
        anotar(perfil);
        anotar(chat);
        anotar("/uploads/" + publicacion);
        anotar("chat/" + UUID.randomUUID() + ".png");  // ya no existe: igual se da por borrada

        assertThat(limpiador.limpiar()).isEqualTo(4);
        assertThat(Files.exists(a)).isFalse();
        assertThat(Files.exists(b)).isFalse();
        assertThat(Files.exists(c)).isFalse();
        assertThat(Files.exists(otraPublicacion)).isTrue();  // lo que no se anotó no se toca
        assertThat(anotadas()).isZero();
        assertThat(limpiador.limpiar()).isZero();
    }

    @Test
    @DisplayName("Una ruta manipulada no borra nada fuera de las fotos permitidas")
    void rutasManipuladas() throws Exception {
        Path afuera = FOTOS.getParent().resolve(FOTOS.getFileName() + "-afuera.txt");
        Files.write(afuera, new byte[]{9});
        Path nota = archivo("nota.txt");
        for (String ruta : new String[]{"../" + afuera.getFileName(), "perfiles/../nota.txt", "/etc/passwd",
                "nota.txt", "/uploads/../nota.txt", "chat/../../" + afuera.getFileName()}) {
            anotar(ruta);
        }
        limpiador.limpiar();
        assertThat(Files.exists(afuera)).isTrue();
        assertThat(Files.exists(nota)).isTrue();
        assertThat(anotadas()).isZero();  // las rutas inválidas se descartan
    }

    @Test
    @DisplayName("Una cuenta ELIMINADO se carga sin errores y no puede iniciar sesión ni usar su sesión")
    void cuentaEliminada() throws Exception {
        Usuario rosa = usuario("rosa-h34@prueba.invalid", EstadoUsuario.ELIMINADO);
        assertThat(usuarios.findById(rosa.getId())).get().extracting(Usuario::getEstado).isEqualTo(EstadoUsuario.ELIMINADO);
        api.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\":\"rosa-h34@prueba.invalid\",\"password\":\"" + PASSWORD + "\"}"))
                .andExpect(status().is4xxClientError())
                .andExpect(jsonPath("$.token").doesNotExist());
        // Una sesión ya abierta tampoco sirve: la cuenta queda deshabilitada
        assertThat(UserPrincipal.from(rosa).isEnabled()).isFalse();
    }

    @Test
    @DisplayName("La persona ve el texto nuevo y, si se rechazó, el motivo")
    void estadoDeLaSolicitud() throws Exception {
        Usuario rosa = usuario("rosa2-h34@prueba.invalid", EstadoUsuario.ACTIVO);
        var yo = UserPrincipal.from(rosa);
        var sesion = authentication(new UsernamePasswordAuthenticationToken(yo, null, yo.getAuthorities()));
        api.perform(post("/api/perfil/solicitud-eliminacion").with(sesion)
                        .contentType(MediaType.APPLICATION_JSON).content("{\"confirmar\":true}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.estado").value("PENDIENTE"))
                .andExpect(jsonPath("$.informacion").value(EliminacionCuentaService.INFORMACION));
        assertThat(EliminacionCuentaService.INFORMACION).contains("10 días hábiles").doesNotContain("No se han eliminado");
        jdbc.update("UPDATE solicitudes_eliminacion SET estado = 'RECHAZADA', motivo_rechazo = ? WHERE usuario_id = ?",
                "La solicitud no la hizo la titular.", rosa.getId());
        api.perform(get("/api/perfil/solicitud-eliminacion").with(sesion)).andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("RECHAZADA"))
                .andExpect(jsonPath("$.motivoRechazo").value("La solicitud no la hizo la titular."));
    }
}
