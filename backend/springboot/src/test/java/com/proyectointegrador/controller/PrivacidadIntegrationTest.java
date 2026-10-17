package com.proyectointegrador.controller;

import com.proyectointegrador.entity.Categoria;
import com.proyectointegrador.entity.Objeto;
import com.proyectointegrador.entity.TipoObjeto;
import com.proyectointegrador.entity.Usuario;
import com.proyectointegrador.repository.CategoriaRepository;
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
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("H19 - Datos de contacto ocultos para los demás usuarios (API + base temporal)")
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:foundia_privacidad_test;MODE=MySQL;DB_CLOSE_DELAY=-1",
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
class PrivacidadIntegrationTest {

    private static final String CORREO_ANA = "ana.secreta@prueba.invalid";
    private static final String APELLIDO_ANA = "Pérezvalle";

    @Autowired
    private MockMvc mockMvc;
    @Autowired
    private UsuarioRepository usuarioRepository;
    @Autowired
    private CategoriaRepository categoriaRepository;
    @Autowired
    private ObjetoRepository objetoRepository;
    @Autowired
    private JwtService jwtService;
    @MockitoBean
    private EmailService emailService;

    private Usuario ana;
    private Categoria categoria;
    private Objeto perdido;
    private String tokenLuis;
    private String tokenAna;

    @BeforeEach
    void preparar() {
        objetoRepository.deleteAll();
        usuarioRepository.deleteAll();
        categoriaRepository.deleteAll();

        ana = usuarioRepository.saveAndFlush(usuario("Ana", APELLIDO_ANA, CORREO_ANA));
        Usuario luis = usuarioRepository.saveAndFlush(usuario("Luis", "Gómez", "luis@prueba.invalid"));
        tokenAna = jwtService.generateToken(UserPrincipal.from(ana));
        tokenLuis = jwtService.generateToken(UserPrincipal.from(luis));

        categoria = new Categoria();
        categoria.setNombre("Mochilas QA");
        categoria = categoriaRepository.saveAndFlush(categoria);

        perdido = objetoRepository.saveAndFlush(objeto(ana, "Mochila azul privada", TipoObjeto.PERDIDO));
        objetoRepository.saveAndFlush(objeto(luis, "Mochila azul encontrada", TipoObjeto.ENCONTRADO));
    }

    private Usuario usuario(String nombre, String apellido, String correo) {
        Usuario u = new Usuario();
        u.setNombre(nombre);
        u.setApellido(apellido);
        u.setCorreo(correo);
        u.setPassword(new BCryptPasswordEncoder(4).encode("Segura123!"));
        u.setVerificado(true);
        u.setCodigoVerificacion("654321");
        return u;
    }

    private Objeto objeto(Usuario autor, String nombre, TipoObjeto tipo) {
        Objeto o = new Objeto();
        o.setUsuario(autor);
        o.setCategoria(categoria);
        o.setNombre(nombre);
        o.setDescripcion("Mochila azul con cuadernos");
        o.setUbicacion("Biblioteca");
        o.setFechaObjeto(LocalDate.now().minusDays(1));
        o.setTipo(tipo);
        return o;
    }

    /** Rutas públicas donde aparece el autor de una publicación. */
    private List<String> rutasPublicas() {
        return List.of(
                "/api/objetos",
                "/api/objetos/" + perdido.getId(),
                "/api/objetos/buscar?q=mochila",
                "/api/objetos/categoria/" + categoria.getId(),
                "/api/objetos/" + perdido.getId() + "/coincidencias");
    }

    private String leer(String ruta, String token) throws Exception {
        var peticion = get(ruta);
        if (token != null) peticion.header("Authorization", "Bearer " + token);
        return mockMvc.perform(peticion).andReturn().getResponse().getContentAsString();
    }

    private void sinDatosPrivados(String ruta, String cuerpo) {
        assertThat(cuerpo).as(ruta).doesNotContain(CORREO_ANA, APELLIDO_ANA, "$2a$", "654321",
                "password", "correo", "intentos", "bloqueado");
    }

    @Test
    @DisplayName("Un visitante sin sesión no recibe datos de contacto ni privados en listados, detalle, búsqueda, categoría ni coincidencias")
    void visitanteNoVeDatosPrivados() throws Exception {
        for (String ruta : rutasPublicas()) {
            sinDatosPrivados(ruta, leer(ruta, null));
        }
    }

    @Test
    @DisplayName("Otro usuario con sesión tampoco recibe el correo ni el apellido completo del autor")
    void otroUsuarioNoVeDatosPrivados() throws Exception {
        for (String ruta : rutasPublicas()) {
            sinDatosPrivados(ruta, leer(ruta, tokenLuis));
        }
    }

    @Test
    @DisplayName("El autor público tiene solo id, nombre e inicial del apellido")
    void autorSoloConVistaPublica() throws Exception {
        mockMvc.perform(get("/api/objetos/" + perdido.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.publicadoPor.id").value(ana.getId()))
                .andExpect(jsonPath("$.publicadoPor.nombre").value("Ana"))
                .andExpect(jsonPath("$.publicadoPor.apellido").value("P."))
                .andExpect(jsonPath("$.publicadoPor.correo").doesNotExist())
                .andExpect(jsonPath("$.publicadoPor.rol").doesNotExist())
                .andExpect(jsonPath("$.publicadoPor.estado").doesNotExist());
    }

    @Test
    @DisplayName("El titular conserva el acceso a su propio correo en /api/perfil")
    void titularVeSuPerfil() throws Exception {
        mockMvc.perform(get("/api/perfil").header("Authorization", "Bearer " + tokenAna))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.correo").value(CORREO_ANA))
                .andExpect(jsonPath("$.apellido").value(APELLIDO_ANA))
                .andExpect(jsonPath("$.password").doesNotExist());
    }

    @Test
    @DisplayName("Otro usuario en /api/perfil solo obtiene su propio perfil, nunca el de la autora")
    void perfilNoDevuelveAOtro() throws Exception {
        String cuerpo = leer("/api/perfil", tokenLuis);
        assertThat(cuerpo).contains("luis@prueba.invalid").doesNotContain(CORREO_ANA, APELLIDO_ANA);
        // No existe una ruta para pedir el perfil de otra persona cambiando un id.
        mockMvc.perform(get("/api/perfil/" + ana.getId()).header("Authorization", "Bearer " + tokenLuis))
                .andExpect(result -> assertThat(result.getResponse().getStatus()).isIn(404, 405));
    }

    @Test
    @DisplayName("Manipular el id o provocar errores no expone datos privados")
    void erroresNoExponenDatos() throws Exception {
        for (String ruta : List.of("/api/objetos/999999", "/api/objetos/abc", "/api/objetos/999999/coincidencias",
                "/api/objetos/categoria/999999")) {
            var respuesta = mockMvc.perform(get(ruta)).andReturn().getResponse();
            assertThat(respuesta.getStatus()).as(ruta).isGreaterThanOrEqualTo(400).isLessThan(500);
            sinDatosPrivados(ruta, respuesta.getContentAsString());
        }
    }

    @Test
    @DisplayName("Sin sesión, /api/perfil devuelve 401 sin datos")
    void perfilSinSesion() throws Exception {
        String cuerpo = mockMvc.perform(get("/api/perfil"))
                .andExpect(status().isUnauthorized())
                .andReturn().getResponse().getContentAsString();
        assertThat(cuerpo).doesNotContain(CORREO_ANA);
    }
}
