package com.proyectointegrador.controller;

import com.proyectointegrador.entity.Categoria;
import com.proyectointegrador.entity.EstadoObjeto;
import com.proyectointegrador.entity.Objeto;
import com.proyectointegrador.entity.Reclamo;
import com.proyectointegrador.entity.Rol;
import com.proyectointegrador.entity.TipoObjeto;
import com.proyectointegrador.entity.Usuario;
import com.proyectointegrador.security.AsistenteRateLimiter;
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
import java.time.LocalDateTime;
import java.util.UUID;

import static org.hamcrest.Matchers.hasItem;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("H45 - asistente: interpretación, límite y datos propios")
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:foundia_asistente_test;MODE=MySQL;DB_CLOSE_DELAY=-1",
        "spring.datasource.driver-class-name=org.h2.Driver",
        "spring.datasource.username=sa",
        "spring.datasource.password=",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.main.banner-mode=off",
        "logging.level.root=WARN",
        "app.asistente.claude.enabled=false",
        "app.asistente.limite-por-minuto=3"
})
@AutoConfigureMockMvc
@Transactional
class AsistenteIntegrationTest {

    @Autowired
    private MockMvc api;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private AsistenteRateLimiter rateLimiter;

    private Categoria categoria;

    @BeforeEach
    void preparar() {
        rateLimiter.limpiar();
        categoria = new Categoria();
        categoria.setNombre("Celular " + UUID.randomUUID());
        categoria.setDescripcion("Teléfonos móviles");
        categoria.setEstado(true);
        entityManager.persist(categoria);
        entityManager.flush();
    }

    private Usuario usuario(String correo) {
        Usuario usuario = new Usuario();
        usuario.setNombre("Ana");
        usuario.setApellido("Prueba");
        usuario.setCorreo(correo);
        usuario.setPassword("no-se-utiliza");
        usuario.setVerificado(true);
        entityManager.persist(usuario);
        return usuario;
    }

    private Objeto objeto(Usuario usuario, String nombre, EstadoObjeto estado) {
        Objeto objeto = new Objeto();
        objeto.setUsuario(usuario);
        objeto.setCategoria(categoria);
        objeto.setNombre(nombre);
        objeto.setDescripcion("Descripción de " + nombre);
        objeto.setUbicacion("Biblioteca");
        objeto.setFechaObjeto(LocalDate.now().minusDays(1));
        objeto.setTipo(TipoObjeto.ENCONTRADO);
        objeto.setEstado(estado);
        entityManager.persist(objeto);
        return objeto;
    }

    private Reclamo reclamo(Usuario usuario, Objeto objeto, String estado,
                            LocalDateTime expira, LocalDateTime usado) {
        Reclamo reclamo = new Reclamo();
        reclamo.setUsuario(usuario);
        reclamo.setObjeto(objeto);
        reclamo.setEstado(estado);
        reclamo.setQrRetiroExpira(expira);
        reclamo.setQrRetiroUsadoEn(usado);
        entityManager.persist(reclamo);
        return reclamo;
    }

    private RequestPostProcessor autenticar(Usuario usuario) {
        UserPrincipal principal = new UserPrincipal(usuario.getId(), usuario.getNombre(), usuario.getCorreo(),
                usuario.getPassword(), Rol.USUARIO);
        return authentication(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    private String cuerpo(String mensaje) {
        return "{\"mensaje\":\"" + mensaje.replace("\"", "\\\"") + "\"}";
    }

    @Test
    @DisplayName("Sin sesión interpreta con el motor local y no devuelve datos propios")
    void interpretaSinSesion() throws Exception {
        api.perform(post("/api/asistente/interpretar")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("Perdí mi celular azul en la biblioteca")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intencion").value("buscar"))
                .andExpect(jsonPath("$.tipoBuscado").value("ENCONTRADO"))
                .andExpect(jsonPath("$.categoria.id").value(categoria.getId()))
                .andExpect(jsonPath("$.ubicacion").value("biblioteca"))
                .andExpect(jsonPath("$.palabras", hasItem("celular")))
                .andExpect(jsonPath("$.colores", hasItem("azul")))
                .andExpect(jsonPath("$.fuente").value("local"))
                .andExpect(jsonPath("$.datosPropios").doesNotExist())
                .andExpect(jsonPath("$.resumenPropio").doesNotExist());
    }

    @Test
    @DisplayName("Con sesión resume publicaciones, reclamos y QR sin datos personales")
    void resumeDatosPropiosSinDatosPersonales() throws Exception {
        Usuario ana = usuario("ana-asistente@prueba.invalid");
        Objeto activo = objeto(ana, "Mochila activa", EstadoObjeto.ACTIVO);
        Objeto custodia = objeto(ana, "Mochila en custodia", EstadoObjeto.EN_CUSTODIA);
        Objeto recuperado = objeto(ana, "Mochila recuperada", EstadoObjeto.RECUPERADO);
        reclamo(ana, activo, "PENDIENTE", null, null);
        reclamo(ana, custodia, "ACEPTADO", LocalDateTime.now().plusDays(2), null);
        reclamo(ana, recuperado, "RETIRADO", LocalDateTime.now().minusDays(1), LocalDateTime.now());
        entityManager.flush();

        String respuesta = api.perform(post("/api/asistente/interpretar")
                        .with(autenticar(ana))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(cuerpo("hola")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intencion").value("saludo"))
                .andExpect(jsonPath("$.datosPropios.publicaciones.total").value(3))
                .andExpect(jsonPath("$.datosPropios.publicaciones.activas").value(1))
                .andExpect(jsonPath("$.datosPropios.publicaciones.enCustodia").value(1))
                .andExpect(jsonPath("$.datosPropios.publicaciones.recuperadas").value(1))
                .andExpect(jsonPath("$.datosPropios.reclamos.total").value(3))
                .andExpect(jsonPath("$.datosPropios.reclamos.pendientes").value(1))
                .andExpect(jsonPath("$.datosPropios.reclamos.aceptados").value(1))
                .andExpect(jsonPath("$.datosPropios.reclamos.retirados").value(1))
                .andExpect(jsonPath("$.datosPropios.qrRetiroVigentes").value(1))
                .andExpect(jsonPath("$.resumenPropio").isNotEmpty())
                .andReturn().getResponse().getContentAsString();

        org.assertj.core.api.Assertions.assertThat(respuesta)
                .doesNotContain(ana.getCorreo())
                .doesNotContain("Ana")
                .doesNotContain("qr_retiro_token")
                .doesNotContain("Mochila activa");
    }

    @Test
    @DisplayName("Valida el mensaje y limita los usos por minuto")
    void validaYLimita() throws Exception {
        api.perform(post("/api/asistente/interpretar")
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest());
        api.perform(post("/api/asistente/interpretar")
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo("x".repeat(301))))
                .andExpect(status().isBadRequest());
        api.perform(post("/api/asistente/interpretar")
                        .contentType(MediaType.APPLICATION_JSON).content("{no-json"))
                .andExpect(status().isBadRequest());

        for (int i = 0; i < 3; i++) {
            api.perform(post("/api/asistente/interpretar")
                            .contentType(MediaType.APPLICATION_JSON).content(cuerpo("hola")))
                    .andExpect(status().isOk());
        }
        api.perform(post("/api/asistente/interpretar")
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo("hola")))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.status").value(429));
    }

    @Test
    @DisplayName("Las intenciones de ayuda, publicación y vacío también responden")
    void otrasIntenciones() throws Exception {
        api.perform(post("/api/asistente/interpretar")
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo("¿Cómo publico un objeto?")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intencion").value("publicar"));
        api.perform(post("/api/asistente/interpretar")
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo("¿Qué puedes hacer?")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intencion").value("ayuda"));
        api.perform(post("/api/asistente/interpretar")
                        .contentType(MediaType.APPLICATION_JSON).content(cuerpo("de la")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intencion").value("vacio"));
    }
}
