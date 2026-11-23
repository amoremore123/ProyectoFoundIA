package com.proyectointegrador.controller;

import com.proyectointegrador.entity.Categoria;
import com.proyectointegrador.entity.EstadoObjeto;
import com.proyectointegrador.entity.Foto;
import com.proyectointegrador.entity.Objeto;
import com.proyectointegrador.entity.Rol;
import com.proyectointegrador.entity.TipoObjeto;
import com.proyectointegrador.entity.Usuario;
import com.proyectointegrador.security.UserPrincipal;
import com.proyectointegrador.service.ClienteIa;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Coincidencias por imagen: la IA compara mi foto con las de mis coincidencias")
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:foundia_comparacion_fotos;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.main.banner-mode=off",
        "logging.level.root=WARN",
        "app.upload.directory=target/test-uploads-comparacion"
})
@AutoConfigureMockMvc
@Transactional
class ComparacionFotosIntegrationTest {

    private static final Path CARPETA = Path.of("target/test-uploads-comparacion").toAbsolutePath().normalize();

    @Autowired private EntityManager entityManager;
    @Autowired private MockMvc api;
    @MockitoBean private ClienteIa clienteIa;

    private Usuario ana;
    private Usuario luis;
    private Categoria categoria;

    @BeforeEach
    void preparar() {
        ana = usuario("ana-fotos@prueba.invalid", "Ana");
        luis = usuario("luis-fotos@prueba.invalid", "Luis");
        categoria = new Categoria();
        categoria.setNombre("Mochilas " + UUID.randomUUID());
        categoria.setEstado(true);
        entityManager.persist(categoria);
        entityManager.flush();
        when(clienteIa.configurado()).thenReturn(true);
    }

    private Usuario usuario(String correo, String nombre) {
        Usuario u = new Usuario();
        u.setNombre(nombre);
        u.setApellido("Prueba");
        u.setCorreo(UUID.randomUUID() + correo);
        u.setPassword("no-se-utiliza");
        u.setVerificado(true);
        entityManager.persist(u);
        return u;
    }

    private Objeto objeto(Usuario autor, TipoObjeto tipo, boolean conFoto) throws IOException {
        Objeto o = new Objeto();
        o.setUsuario(autor);
        o.setCategoria(categoria);
        o.setNombre("Mochila azul");
        o.setDescripcion("Prueba");
        o.setUbicacion("Biblioteca");
        o.setFechaObjeto(LocalDate.now().minusDays(1));
        o.setTipo(tipo);
        o.setEstado(EstadoObjeto.ACTIVO);
        entityManager.persist(o);
        if (conFoto) {
            String nombre = UUID.randomUUID() + ".png";
            Files.createDirectories(CARPETA);
            ImageIO.write(new BufferedImage(1200, 900, BufferedImage.TYPE_INT_RGB), "png", CARPETA.resolve(nombre).toFile());
            Foto f = new Foto();
            f.setObjeto(o);
            f.setUrl("/uploads/" + nombre);
            f.setNombreArchivo(nombre);
            entityManager.persist(f);
            o.getFotos().add(f);
        }
        entityManager.flush();
        return o;
    }

    private RequestPostProcessor como(Usuario usuario) {
        UserPrincipal principal = new UserPrincipal(usuario.getId(), usuario.getNombre(), usuario.getCorreo(),
                usuario.getPassword(), Rol.USUARIO);
        return authentication(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    private String ruta(Objeto o) {
        return "/api/objetos/" + o.getId() + "/coincidencias/comparar-fotos";
    }

    @Test
    void comparaLasFotosDeLasCoincidenciasYGuardaElResultadoDelPar() throws Exception {
        Objeto mio = objeto(ana, TipoObjeto.PERDIDO, true);
        Objeto candidato = objeto(luis, TipoObjeto.ENCONTRADO, true);
        Objeto otro = objeto(luis, TipoObjeto.ENCONTRADO, true);
        objeto(luis, TipoObjeto.ENCONTRADO, false); // coincidencia sin foto: solo se cuenta
        when(clienteIa.completarJsonConImagenes(anyString(), anyString(), anyList()))
                .thenReturn("{\"resultados\": [{\"foto\": 2, \"parecido\": 130, \"motivo\": \"Mismo llavero; escribe a ana@correo.com\"},"
                        + " {\"foto\": 3, \"parecido\": 20, \"motivo\": \"Otro modelo y otro color\"}, {\"foto\": 9, \"parecido\": 99}]}");

        api.perform(post(ruta(mio)).with(como(ana)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sinFoto").value(1))
                .andExpect(jsonPath("$.resultados.length()").value(2))
                .andExpect(jsonPath("$.resultados[0].objetoId").value(candidato.getId()))
                .andExpect(jsonPath("$.resultados[0].coincidenciaId").value(mio.getId() + "-" + candidato.getId()))
                .andExpect(jsonPath("$.resultados[0].parecido").value(100))
                .andExpect(jsonPath("$.resultados[0].nivel").value("ALTO"))
                // El motivo con un correo se descarta
                .andExpect(jsonPath("$.resultados[0].motivo").value(""))
                .andExpect(jsonPath("$.resultados[1].objetoId").value(otro.getId()))
                .andExpect(jsonPath("$.resultados[1].nivel").value("BAJO"))
                .andExpect(jsonPath("$.resultados[1].motivo").value("Otro modelo y otro color"));

        // Una sola consulta con mi foto y las dos candidatas, reducidas como JPEG
        verify(clienteIa).completarJsonConImagenes(anyString(), anyString(), argThat((List<String> fotos) ->
                fotos.size() == 3 && fotos.stream().allMatch(f -> f.startsWith("data:image/jpeg;base64,"))));

        // Pulsar otra vez no vuelve a gastar el cupo de la IA
        api.perform(post(ruta(mio)).with(como(ana))).andExpect(status().isOk())
                .andExpect(jsonPath("$.resultados[0].nivel").value("ALTO"));
        verify(clienteIa, times(1)).completarJsonConImagenes(anyString(), anyString(), anyList());
    }

    @Test
    void soloQuienPublicoYConFoto() throws Exception {
        Objeto mio = objeto(ana, TipoObjeto.PERDIDO, true);
        Objeto sinFoto = objeto(ana, TipoObjeto.PERDIDO, false);

        api.perform(post(ruta(mio))).andExpect(status().isUnauthorized());
        api.perform(post(ruta(mio)).with(como(luis))).andExpect(status().isNotFound());
        api.perform(post(ruta(sinFoto)).with(como(ana))).andExpect(status().isBadRequest());
        verify(clienteIa, never()).completarJsonConImagenes(anyString(), anyString(), anyList());
    }

    @Test
    void siLaIaFallaOEstaApagadaLoAvisa() throws Exception {
        Objeto mio = objeto(ana, TipoObjeto.PERDIDO, true);
        objeto(luis, TipoObjeto.ENCONTRADO, true);
        when(clienteIa.completarJsonConImagenes(anyString(), anyString(), anyList())).thenReturn("no es json");
        api.perform(post(ruta(mio)).with(como(ana))).andExpect(status().isServiceUnavailable());

        when(clienteIa.configurado()).thenReturn(false);
        api.perform(post(ruta(mio)).with(como(ana))).andExpect(status().isServiceUnavailable());
    }

    @Test
    void sinCoincidenciasConFotoNoLlamaALaIa() throws Exception {
        Objeto mio = objeto(ana, TipoObjeto.PERDIDO, true);
        api.perform(post(ruta(mio)).with(como(ana)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.resultados.length()").value(0))
                .andExpect(jsonPath("$.sinFoto").value(0));
        verify(clienteIa, never()).completarJsonConImagenes(anyString(), anyString(), anyList());
        assertThat(com.proyectointegrador.service.ComparacionFotosService.nivel(50)).isEqualTo("MEDIO");
    }
}
