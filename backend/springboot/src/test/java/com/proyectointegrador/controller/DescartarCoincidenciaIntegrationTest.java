package com.proyectointegrador.controller;

import com.proyectointegrador.dto.ContactoRequest;
import com.proyectointegrador.entity.*;
import com.proyectointegrador.exception.EstadoCoincidenciaInvalidoException;
import com.proyectointegrador.repository.*;
import com.proyectointegrador.security.UserPrincipal;
import com.proyectointegrador.service.ContactoService;
import com.proyectointegrador.service.GestionCoincidenciaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("H21 - Descartar coincidencias con permisos y persistencia")
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:descarte_s2;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa",
        "spring.datasource.password=", "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.main.banner-mode=off", "logging.level.root=WARN"
})
@AutoConfigureMockMvc
class DescartarCoincidenciaIntegrationTest {
    @Autowired MockMvc api;
    @Autowired UsuarioRepository usuarios;
    @Autowired CategoriaRepository categorias;
    @Autowired ObjetoRepository objetos;
    @Autowired CoincidenciaRepository coincidencias;
    @Autowired ContactoRepository contactos;
    @Autowired NotificacionRepository notificaciones;
    @Autowired GestionCoincidenciaService gestion;
    @Autowired ContactoService contacto;
    Usuario ana, luis, tercero;
    Categoria categoria;
    Objeto perdido, encontrado;

    @BeforeEach
    void preparar() {
        contactos.deleteAllInBatch();
        notificaciones.deleteAllInBatch();
        coincidencias.deleteAllInBatch();
        objetos.deleteAllInBatch();
        categorias.deleteAllInBatch();
        usuarios.deleteAllInBatch();
        ana = usuario("Ana"); luis = usuario("Luis"); tercero = usuario("Carla");
        categoria = new Categoria(); categoria.setNombre("Categoría " + UUID.randomUUID());
        categoria = categorias.saveAndFlush(categoria);
        // El hallazgo tiene ID menor: la clave sigue siendo perdido-encontrado.
        encontrado = objeto(luis, TipoObjeto.ENCONTRADO);
        perdido = objeto(ana, TipoObjeto.PERDIDO);
    }

    private Usuario usuario(String nombre) {
        Usuario u = new Usuario(); u.setNombre(nombre); u.setApellido("Apellido reservado");
        u.setCorreo(UUID.randomUUID() + "@prueba.invalid"); u.setPassword("no-se-usa"); u.setVerificado(true);
        return usuarios.saveAndFlush(u);
    }

    private Objeto objeto(Usuario autor, TipoObjeto tipo) {
        Objeto o = new Objeto(); o.setUsuario(autor); o.setCategoria(categoria);
        o.setNombre("Mochila azul"); o.setDescripcion("Objeto ficticio QA");
        o.setTipo(tipo); o.setUbicacion("Biblioteca"); o.setFechaObjeto(LocalDate.now().minusDays(1));
        return objetos.saveAndFlush(o);
    }

    private RequestPostProcessor como(Usuario usuario) {
        UserPrincipal principal = new UserPrincipal(usuario.getId(), usuario.getNombre(), usuario.getCorreo(),
                usuario.getPassword(), usuario.getRol());
        return authentication(new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));
    }

    private String pareja() { return perdido.getId() + "-" + encontrado.getId(); }
    private String ruta() { return "/api/coincidencias/" + pareja() + "/descartar"; }

    private Coincidencia fila(EstadoCoincidencia estado) {
        Coincidencia c = new Coincidencia(); c.setObjetoPerdido(perdido); c.setObjetoEncontrado(encontrado);
        c.setPorcentaje(new BigDecimal("65")); c.setNivel(NivelCoincidencia.MEDIA); c.setEstado(estado);
        return coincidencias.saveAndFlush(c);
    }

    @Test
    void soloElTitularPuedeDescartarSinDatosPrivados() throws Exception {
        String body = api.perform(post(ruta()).with(como(ana))).andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(pareja()))
                .andExpect(jsonPath("$.estado").value("DESCARTADA"))
                .andExpect(jsonPath("$.repetido").value(false)).andReturn().getResponse().getContentAsString();
        assertThat(body).doesNotContain(ana.getCorreo(), luis.getCorreo(), "Apellido reservado", "password");
        Coincidencia guardada = coincidencias.findByObjetoPerdidoIdAndObjetoEncontradoId(perdido.getId(), encontrado.getId()).orElseThrow();
        assertThat(guardada.getEstado()).isEqualTo(EstadoCoincidencia.DESCARTADA);
        assertThat(guardada.getPorcentaje()).isEqualByComparingTo("100");
        assertThat(objetos.findById(perdido.getId()).orElseThrow().getEstado()).isEqualTo(EstadoObjeto.ACTIVO);
        assertThat(objetos.findById(encontrado.getId()).orElseThrow().getEstado()).isEqualTo(EstadoObjeto.ACTIVO);
        assertThat(contactos.count()).isZero(); assertThat(notificaciones.count()).isZero();
    }

    @Test
    void sesionActorYAdministradorAjenoNoSePuedenSuplantar() throws Exception {
        api.perform(post(ruta())).andExpect(status().isUnauthorized());
        for (Usuario actor : new Usuario[]{luis, tercero}) {
            api.perform(post(ruta()).with(como(actor)).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"usuarioId\":" + ana.getId() + "}"))
                    .andExpect(status().isForbidden());
        }
        tercero.setRol(Rol.ADMIN); tercero = usuarios.saveAndFlush(tercero);
        api.perform(post(ruta()).with(como(tercero))).andExpect(status().isForbidden());
        assertThat(coincidencias.count()).isZero();
    }

    @Test
    void seRepitePorClaveOIdPersistidoSinDuplicarNiCambiarHistoria() throws Exception {
        Coincidencia anterior = fila(EstadoCoincidencia.REVISADA);
        var fecha = anterior.getFechaCreacion();
        api.perform(post("/api/coincidencias/" + anterior.getId() + "/descartar").with(como(ana)))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(pareja()));
        api.perform(post(ruta()).with(como(ana))).andExpect(status().isOk())
                .andExpect(jsonPath("$.repetido").value(true));
        Coincidencia actual = coincidencias.findById(anterior.getId()).orElseThrow();
        assertThat(coincidencias.count()).isEqualTo(1);
        assertThat(actual.getPorcentaje()).isEqualByComparingTo("65");
        assertThat(actual.getFechaCreacion()).isEqualTo(fecha);
    }

    @Test
    void noReapareceEnNingunSentidoNiEnDetalleYConservaOtrasParejas() throws Exception {
        Objeto otro = objeto(luis, TipoObjeto.ENCONTRADO);
        api.perform(post(ruta()).with(como(ana))).andExpect(status().isOk());
        Long id = coincidencias.findAll().get(0).getId();
        for (int i = 0; i < 2; i++) {
            api.perform(get("/api/objetos/" + perdido.getId() + "/coincidencias"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(1))
                    .andExpect(jsonPath("$[0].objetoId").value(otro.getId()));
            api.perform(get("/api/objetos/" + encontrado.getId() + "/coincidencias"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
            api.perform(get("/api/coincidencias/" + pareja())).andExpect(status().isNotFound());
            api.perform(get("/api/coincidencias/" + id)).andExpect(status().isNotFound());
        }
        api.perform(get("/api/coincidencias/" + perdido.getId() + "-" + otro.getId())).andExpect(status().isOk());
        assertThat(coincidencias.count()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(strings = {"abc", "0-1", "1-0", "-1", "1-2-3", "999999999999999999999-1", "99999999"})
    void claveInvalidaEs404SinEscrituras(String id) throws Exception {
        api.perform(post("/api/coincidencias/" + id + "/descartar").with(como(ana)))
                .andExpect(status().isNotFound());
        assertThat(coincidencias.count()).isZero();
    }

    @ParameterizedTest
    @EnumSource(value = EstadoObjeto.class, names = {"OCULTO", "ELIMINADO", "RECUPERADO"})
    void noGestionaObjetosNoDisponibles(EstadoObjeto estado) throws Exception {
        encontrado.setEstado(estado); objetos.saveAndFlush(encontrado);
        api.perform(post(ruta()).with(como(ana)))
                .andExpect(estado == EstadoObjeto.RECUPERADO ? status().isConflict() : status().isNotFound());
        assertThat(coincidencias.count()).isZero();
    }

    @Test
    void noCambiaUnaAceptadaNiPermiteContactarTrasDescartar() throws Exception {
        Coincidencia aceptada = fila(EstadoCoincidencia.ACEPTADA);
        api.perform(post(ruta()).with(como(ana))).andExpect(status().isConflict());
        assertThat(coincidencias.findById(aceptada.getId()).orElseThrow().getEstado()).isEqualTo(EstadoCoincidencia.ACEPTADA);
        coincidencias.deleteAllInBatch();
        api.perform(post(ruta()).with(como(ana))).andExpect(status().isOk());
        api.perform(post("/api/contactos").with(como(ana)).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"objetoPerdidoId\":" + perdido.getId() + ",\"objetoEncontradoId\":" + encontrado.getId() + "}"))
                .andExpect(status().isConflict());
        assertThat(contactos.count()).isZero(); assertThat(notificaciones.count()).isZero();
    }

    @Test
    void parejasIncompatiblesOConBajaSimilitudNoSePersisten() throws Exception {
        encontrado.setTipo(TipoObjeto.PERDIDO); objetos.saveAndFlush(encontrado);
        api.perform(post(ruta()).with(como(ana))).andExpect(status().isNotFound());
        encontrado.setTipo(TipoObjeto.ENCONTRADO); encontrado.setUsuario(ana); objetos.saveAndFlush(encontrado);
        api.perform(post(ruta()).with(como(ana))).andExpect(status().isNotFound());
        encontrado.setUsuario(luis); encontrado.setNombre("Llaves rojas"); encontrado.setUbicacion("Comedor");
        encontrado.setFechaObjeto(LocalDate.now().minusDays(60)); objetos.saveAndFlush(encontrado);
        api.perform(post(ruta()).with(como(ana))).andExpect(status().isNotFound());
        assertThat(coincidencias.count()).isZero();
    }

    @Test
    void otraCategoriaYClaveInvertidaNoSonLaPareja() throws Exception {
        api.perform(post("/api/coincidencias/" + encontrado.getId() + "-" + perdido.getId() + "/descartar")
                .with(como(ana))).andExpect(status().isForbidden());
        Categoria otra = new Categoria(); otra.setNombre("Otra categoría");
        encontrado.setCategoria(categorias.saveAndFlush(otra)); objetos.saveAndFlush(encontrado);
        api.perform(post(ruta()).with(como(ana))).andExpect(status().isNotFound());
        assertThat(coincidencias.count()).isZero();
    }

    @Test
    void dosDescartesSimultaneosCreanUnaFila() throws Exception {
        var pool = Executors.newFixedThreadPool(2);
        var salida = new CountDownLatch(1);
        try {
            var primero = pool.submit(() -> { salida.await(5, TimeUnit.SECONDS); return gestion.descartar(pareja(), ana.getId()); });
            var segundo = pool.submit(() -> { salida.await(5, TimeUnit.SECONDS); return gestion.descartar(pareja(), ana.getId()); });
            salida.countDown();
            var a = primero.get(20, TimeUnit.SECONDS); var c = segundo.get(20, TimeUnit.SECONDS);
            assertThat(a.estado()).isEqualTo("DESCARTADA"); assertThat(c.estado()).isEqualTo("DESCARTADA");
            assertThat(a.repetido()).isNotEqualTo(c.repetido());
            assertThat(coincidencias.count()).isEqualTo(1);
        } finally { pool.shutdownNow(); }
    }

    @Test
    void contactoYDescarteConcurrentesNoDuplicanNiRevivenLaPareja() throws Exception {
        var pool = Executors.newFixedThreadPool(2);
        var salida = new CountDownLatch(1);
        try {
            var descartar = pool.submit(() -> { salida.await(5, TimeUnit.SECONDS); return gestion.descartar(pareja(), ana.getId()); });
            var contactar = pool.submit(() -> {
                salida.await(5, TimeUnit.SECONDS);
                try { contacto.iniciar(new ContactoRequest(perdido.getId(), encontrado.getId()), ana.getId()); return true; }
                catch (EstadoCoincidenciaInvalidoException ex) { return false; }
            });
            salida.countDown();
            assertThat(descartar.get(20, TimeUnit.SECONDS).estado()).isEqualTo("DESCARTADA");
            boolean contactoPrevio = contactar.get(20, TimeUnit.SECONDS);
            assertThat(contactos.count()).isEqualTo(contactoPrevio ? 1 : 0);
            assertThat(notificaciones.count()).isEqualTo(contactoPrevio ? 1 : 0);
            assertThat(coincidencias.count()).isEqualTo(1);
            assertThat(coincidencias.findAll().get(0).getEstado()).isEqualTo(EstadoCoincidencia.DESCARTADA);
        } finally { pool.shutdownNow(); }
    }
}
