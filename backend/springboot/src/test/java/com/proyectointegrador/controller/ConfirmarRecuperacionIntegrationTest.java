package com.proyectointegrador.controller;

import com.jayway.jsonpath.JsonPath;
import com.proyectointegrador.dto.ObjetoRequest;
import com.proyectointegrador.entity.*;
import com.proyectointegrador.exception.EstadoCoincidenciaInvalidoException;
import com.proyectointegrador.repository.*;
import com.proyectointegrador.security.UserPrincipal;
import com.proyectointegrador.service.GestionCoincidenciaService;
import com.proyectointegrador.service.ObjetoService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@DisplayName("H22 - Confirmación atómica de recuperación")
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:recuperacion_s2;MODE=MySQL;DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=10000",
        "spring.datasource.driver-class-name=org.h2.Driver", "spring.datasource.username=sa",
        "spring.datasource.password=", "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.main.banner-mode=off", "logging.level.root=WARN"
})
@AutoConfigureMockMvc
class ConfirmarRecuperacionIntegrationTest {
    @Autowired MockMvc api;
    @Autowired UsuarioRepository usuarios;
    @Autowired CategoriaRepository categorias;
    @Autowired ObjetoRepository objetos;
    @Autowired CoincidenciaRepository coincidencias;
    @Autowired ContactoRepository contactos;
    @Autowired NotificacionRepository notificaciones;
    @Autowired AvisoCoincidenciaRepository avisos;
    @Autowired EventoCoincidenciaRepository eventos;
    @Autowired @org.springframework.beans.factory.annotation.Qualifier("avisosCoincidenciaExecutor") java.util.concurrent.Executor avisosExecutor;
    @Autowired GestionCoincidenciaService gestion;
    @Autowired ObjetoService publicaciones;
    @Autowired JdbcTemplate jdbc;
    Usuario ana, luis, tercero;
    Categoria categoria;
    Objeto perdido, encontrado;
    static final String CONFIRMACION = "{\"confirmarRecuperacion\":true}";

    @BeforeEach
    void preparar() {
        esperarAvisos();
        eventos.deleteAllInBatch();
        avisos.deleteAllInBatch();
        contactos.deleteAllInBatch(); notificaciones.deleteAllInBatch(); coincidencias.deleteAllInBatch();
        objetos.deleteAllInBatch(); categorias.deleteAllInBatch(); usuarios.deleteAllInBatch();
        ana = usuario("Ana"); luis = usuario("Luis"); tercero = usuario("Carla");
        categoria = new Categoria(); categoria.setNombre("Recuperación " + UUID.randomUUID());
        categoria = categorias.saveAndFlush(categoria);
        encontrado = objeto(luis, TipoObjeto.ENCONTRADO); perdido = objeto(ana, TipoObjeto.PERDIDO);
    }

    @AfterEach void esperarAvisos(){Sprint2Fixtures.esperarAvisos(avisosExecutor);}

    private Usuario usuario(String nombre) {
        Usuario u = new Usuario(); u.setNombre(nombre); u.setApellido("Apellido reservado");
        u.setCorreo(UUID.randomUUID() + "@prueba.invalid"); u.setPassword("no-se-usa"); u.setVerificado(true);
        return usuarios.saveAndFlush(u);
    }

    private Objeto objeto(Usuario autor, TipoObjeto tipo) {
        Objeto o = new Objeto(); o.setUsuario(autor); o.setCategoria(categoria); o.setTipo(tipo);
        o.setNombre("Mochila azul"); o.setDescripcion("Objeto ficticio QA"); o.setUbicacion("Biblioteca");
        o.setFechaObjeto(LocalDate.now().minusDays(1)); return objetos.saveAndFlush(o);
    }

    private RequestPostProcessor como(Usuario usuario) {
        UserPrincipal p = new UserPrincipal(usuario.getId(), usuario.getNombre(), usuario.getCorreo(), usuario.getPassword(), usuario.getRol());
        return authentication(new UsernamePasswordAuthenticationToken(p, null, p.getAuthorities()));
    }

    private String pareja() { return perdido.getId() + "-" + encontrado.getId(); }
    private String ruta() { return "/api/coincidencias/" + pareja() + "/confirmar"; }

    private Coincidencia fila(Objeto hallazgo, EstadoCoincidencia estado) {
        Coincidencia c = new Coincidencia(); c.setObjetoPerdido(perdido); c.setObjetoEncontrado(hallazgo);
        c.setEstado(estado); c.setPorcentaje(new BigDecimal("65")); c.setNivel(NivelCoincidencia.MEDIA);
        return coincidencias.saveAndFlush(c);
    }

    @Test
    void confirmaSoloElPerdidoPropioYDevuelveFechaUtcSinDatosPrivados() throws Exception {
        Instant antes = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        var actualizacionHallazgo = encontrado.getFechaActualizacion();
        String body = api.perform(post(ruta()).with(como(ana)).contentType(MediaType.APPLICATION_JSON).content(CONFIRMACION))
                .andExpect(status().isOk()).andExpect(jsonPath("$.id").value(pareja()))
                .andExpect(jsonPath("$.estado").value("ACEPTADA"))
                .andExpect(jsonPath("$.objetoPerdidoId").value(perdido.getId()))
                .andExpect(jsonPath("$.estadoObjetoPerdido").value("RECUPERADO"))
                .andExpect(jsonPath("$.repetido").value(false)).andReturn().getResponse().getContentAsString();
        Instant fecha = Instant.parse(JsonPath.read(body, "$.fechaRecuperacion"));
        assertThat(fecha).isBetween(antes, Instant.now());
        assertThat(body).doesNotContain(ana.getCorreo(), luis.getCorreo(), "Apellido reservado", "password");
        Objeto actual = objetos.findById(perdido.getId()).orElseThrow();
        assertThat(actual.getEstado()).isEqualTo(EstadoObjeto.RECUPERADO);
        assertThat(actual.getFechaRecuperacion().toInstant(ZoneOffset.UTC)).isEqualTo(fecha);
        Objeto hallazgo = objetos.findById(encontrado.getId()).orElseThrow();
        assertThat(hallazgo.getEstado()).isEqualTo(EstadoObjeto.ACTIVO);
        assertThat(hallazgo.getFechaRecuperacion()).isNull();
        assertThat(hallazgo.getFechaActualizacion()).isEqualTo(actualizacionHallazgo);
        assertThat(contactos.count()).isZero(); assertThat(notificaciones.count()).isZero();
        api.perform(get("/api/objetos/" + perdido.getId())).andExpect(status().isOk())
                .andExpect(jsonPath("$.estado").value("RECUPERADO")).andExpect(jsonPath("$.fechaRecuperacion").value(fecha.toString()));
        api.perform(get("/api/perfil/objetos").with(como(ana))).andExpect(status().isOk())
                .andExpect(jsonPath("$[0].fechaRecuperacion").value(fecha.toString()));
        api.perform(get("/api/objetos/" + perdido.getId() + "/coincidencias"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        api.perform(get("/api/objetos/" + encontrado.getId() + "/coincidencias"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
        api.perform(get("/api/coincidencias/" + pareja())).andExpect(status().isNotFound());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "{}", "null", "{\"confirmarRecuperacion\":null}", "{\"confirmarRecuperacion\":false}"})
    void exigeConfirmacionExplicitaSinExitoAnticipado(String body) throws Exception {
        api.perform(post(ruta()).with(como(ana)).contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest());
        assertThat(coincidencias.count()).isZero();
        assertThat(objetos.findById(perdido.getId()).orElseThrow().getEstado()).isEqualTo(EstadoObjeto.ACTIVO);
    }

    @Test
    void soloLaSesionDelTitularPuedeConfirmar() throws Exception {
        api.perform(post(ruta()).contentType(MediaType.APPLICATION_JSON).content(CONFIRMACION)).andExpect(status().isUnauthorized());
        tercero.setRol(Rol.ADMIN); tercero = usuarios.saveAndFlush(tercero);
        for (Usuario actor : new Usuario[]{luis, tercero}) {
            api.perform(post(ruta()).with(como(actor)).contentType(MediaType.APPLICATION_JSON)
                            .content("{\"confirmarRecuperacion\":true,\"usuarioId\":" + ana.getId() + "}"))
                    .andExpect(status().isForbidden());
        }
        assertThat(coincidencias.count()).isZero();
    }

    @ParameterizedTest
    @EnumSource(value = EstadoCoincidencia.class, names = {"PENDIENTE", "REVISADA"})
    void conservaPuntuacionYFechaHistoricasAlAceptarYRepetir(EstadoCoincidencia estado) throws Exception {
        Coincidencia anterior = fila(encontrado, estado);
        var primera = gestion.confirmar(anterior.getId().toString(), ana.getId());
        encontrado.setNombre("Texto corregido"); encontrado.setUbicacion("Otro lugar"); objetos.saveAndFlush(encontrado);
        var segunda = gestion.confirmar(pareja(), ana.getId());
        assertThat(segunda.repetido()).isTrue(); assertThat(segunda.fechaRecuperacion()).isEqualTo(primera.fechaRecuperacion());
        api.perform(post("/api/coincidencias/" + anterior.getId() + "/confirmar").with(como(ana))
                        .contentType(MediaType.APPLICATION_JSON).content(CONFIRMACION))
                .andExpect(status().isOk()).andExpect(jsonPath("$.repetido").value(true))
                .andExpect(jsonPath("$.fechaRecuperacion").value(primera.fechaRecuperacion().toString()));
        Coincidencia actual = coincidencias.findById(anterior.getId()).orElseThrow();
        assertThat(coincidencias.count()).isEqualTo(1); assertThat(actual.getPorcentaje()).isEqualByComparingTo("65");
        assertThat(actual.getFechaCreacion()).isEqualTo(anterior.getFechaCreacion());
        api.perform(post(ruta()).with(como(luis)).contentType(MediaType.APPLICATION_JSON).content(CONFIRMACION))
                .andExpect(status().isForbidden());
    }

    @ParameterizedTest
    @EnumSource(value = EstadoObjeto.class, names = {"OCULTO", "ELIMINADO", "RECUPERADO"})
    void rechazaEstadosIncompatiblesDeAmbosObjetos(EstadoObjeto estado) throws Exception {
        for (Objeto objeto : new Objeto[]{perdido, encontrado}) {
            objeto.setEstado(estado); objetos.saveAndFlush(objeto);
            api.perform(post(ruta()).with(como(ana)).contentType(MediaType.APPLICATION_JSON).content(CONFIRMACION))
                    .andExpect(estado == EstadoObjeto.RECUPERADO ? status().isConflict() : status().isNotFound());
            objeto.setEstado(EstadoObjeto.ACTIVO); objetos.saveAndFlush(objeto);
        }
        assertThat(coincidencias.count()).isZero();
    }

    @Test
    void noAceptaDescartadasNiOtraParejaNiInventaFechasHistoricas() throws Exception {
        Coincidencia c = fila(encontrado, EstadoCoincidencia.DESCARTADA);
        api.perform(post(ruta()).with(como(ana)).contentType(MediaType.APPLICATION_JSON).content(CONFIRMACION)).andExpect(status().isConflict());
        c.setEstado(EstadoCoincidencia.ACEPTADA); coincidencias.saveAndFlush(c);
        api.perform(post(ruta()).with(como(ana)).contentType(MediaType.APPLICATION_JSON).content(CONFIRMACION)).andExpect(status().isConflict());
        Objeto otro = objeto(luis, TipoObjeto.ENCONTRADO);
        api.perform(post("/api/coincidencias/" + perdido.getId() + "-" + otro.getId() + "/confirmar").with(como(ana))
                .contentType(MediaType.APPLICATION_JSON).content(CONFIRMACION)).andExpect(status().isConflict());
        perdido.setEstado(EstadoObjeto.RECUPERADO); objetos.saveAndFlush(perdido);
        api.perform(post(ruta()).with(como(ana)).contentType(MediaType.APPLICATION_JSON).content(CONFIRMACION)).andExpect(status().isConflict());
        assertThat(objetos.findById(perdido.getId()).orElseThrow().getFechaRecuperacion()).isNull();
        assertThat(coincidencias.count()).isEqualTo(1);
    }

    @Test
    void claveParejaYCategoriaSeRevalidanAntesDeGuardar() throws Exception {
        for (String id : new String[]{"abc", "0-1", "1-0", "999999999", "99999999999999999999-1"}) {
            api.perform(post("/api/coincidencias/" + id + "/confirmar").with(como(ana))
                    .contentType(MediaType.APPLICATION_JSON).content(CONFIRMACION)).andExpect(status().isNotFound());
        }
        encontrado.setTipo(TipoObjeto.PERDIDO); objetos.saveAndFlush(encontrado);
        api.perform(post(ruta()).with(como(ana)).contentType(MediaType.APPLICATION_JSON).content(CONFIRMACION)).andExpect(status().isNotFound());
        encontrado.setTipo(TipoObjeto.ENCONTRADO); encontrado.setUsuario(ana); objetos.saveAndFlush(encontrado);
        api.perform(post(ruta()).with(como(ana)).contentType(MediaType.APPLICATION_JSON).content(CONFIRMACION)).andExpect(status().isNotFound());
        Categoria otra = new Categoria(); otra.setNombre("Otra categoría"); encontrado.setCategoria(categorias.saveAndFlush(otra));
        encontrado.setUsuario(luis); objetos.saveAndFlush(encontrado);
        api.perform(post(ruta()).with(como(ana)).contentType(MediaType.APPLICATION_JSON).content(CONFIRMACION)).andExpect(status().isNotFound());
        encontrado.setCategoria(categoria); encontrado.setNombre("Llaves rojas"); encontrado.setUbicacion("Comedor");
        encontrado.setFechaObjeto(LocalDate.now().minusDays(60)); objetos.saveAndFlush(encontrado);
        api.perform(post(ruta()).with(como(ana)).contentType(MediaType.APPLICATION_JSON).content(CONFIRMACION)).andExpect(status().isNotFound());
        assertThat(coincidencias.count()).isZero();
    }

    private <T> java.util.List<T> simultaneos(Callable<T> uno, Callable<T> dos) throws Exception {
        var pool = Executors.newFixedThreadPool(2);
        var listos = new CountDownLatch(2); var salida = new CountDownLatch(1);
        try {
            var a = pool.submit(() -> { listos.countDown(); if (!salida.await(10, TimeUnit.SECONDS)) throw new AssertionError("Sin salida"); return uno.call(); });
            var b = pool.submit(() -> { listos.countDown(); if (!salida.await(10, TimeUnit.SECONDS)) throw new AssertionError("Sin salida"); return dos.call(); });
            assertThat(listos.await(10, TimeUnit.SECONDS)).isTrue(); salida.countDown();
            return java.util.List.of(a.get(30, TimeUnit.SECONDS), b.get(30, TimeUnit.SECONDS));
        } finally { salida.countDown(); pool.shutdownNow(); }
    }

    private String intentar(String id) {
        try { gestion.confirmar(id, ana.getId()); return "ACEPTADA"; }
        catch (EstadoCoincidenciaInvalidoException ex) { return "CONFLICTO"; }
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void dosConfirmacionesDeLaMismaParejaSonIdempotentes(boolean idPersistido) throws Exception {
        String id = idPersistido ? fila(encontrado, EstadoCoincidencia.REVISADA).getId().toString() : pareja();
        var respuestas = simultaneos(() -> gestion.confirmar(id, ana.getId()), () -> gestion.confirmar(id, ana.getId()));
        assertThat(respuestas).extracting(r -> r.repetido()).containsExactlyInAnyOrder(false, true);
        assertThat(respuestas.get(0).fechaRecuperacion()).isEqualTo(respuestas.get(1).fechaRecuperacion());
        assertThat(coincidencias.count()).isEqualTo(1);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void dosParejasCompitenPorUnSoloPerdido(boolean idsPersistidos) throws Exception {
        Objeto otro = objeto(luis, TipoObjeto.ENCONTRADO);
        String a = idsPersistidos ? fila(encontrado, EstadoCoincidencia.PENDIENTE).getId().toString() : pareja();
        String b = idsPersistidos ? fila(otro, EstadoCoincidencia.PENDIENTE).getId().toString() : perdido.getId() + "-" + otro.getId();
        assertThat(simultaneos(() -> intentar(a), () -> intentar(b))).containsExactlyInAnyOrder("ACEPTADA", "CONFLICTO");
        assertThat(coincidencias.findAll()).filteredOn(c -> c.getEstado() == EstadoCoincidencia.ACEPTADA).hasSize(1);
        assertThat(coincidencias.count()).isEqualTo(idsPersistidos ? 2 : 1);
        assertThat(objetos.findById(perdido.getId()).orElseThrow().getEstado()).isEqualTo(EstadoObjeto.RECUPERADO);
        for (Objeto hallazgo : new Objeto[]{encontrado, otro}) {
            assertThat(objetos.findById(hallazgo.getId()).orElseThrow().getEstado()).isEqualTo(EstadoObjeto.ACTIVO);
            assertThat(objetos.findById(hallazgo.getId()).orElseThrow().getFechaRecuperacion()).isNull();
        }
    }

    @Test
    void confirmacionYDescarteConcurrentesNoDejanEstadosParciales() throws Exception {
        var respuestas = simultaneos(() -> intentar(pareja()), () -> {
            try { gestion.descartar(pareja(), ana.getId()); return "DESCARTADA"; }
            catch (EstadoCoincidenciaInvalidoException ex) { return "CONFLICTO"; }
        });
        assertThat(respuestas).contains("CONFLICTO");
        assertThat(coincidencias.count()).isEqualTo(1);
        Coincidencia c = coincidencias.findAll().get(0); Objeto o = objetos.findById(perdido.getId()).orElseThrow();
        assertThat(o.getEstado()).isEqualTo(c.getEstado() == EstadoCoincidencia.ACEPTADA ? EstadoObjeto.RECUPERADO : EstadoObjeto.ACTIVO);
        assertThat(o.getFechaRecuperacion() != null).isEqualTo(c.getEstado() == EstadoCoincidencia.ACEPTADA);
    }

    @RepeatedTest(10)
    void edicionConcurrenteNoSobrescribeRecuperacionNiFecha() throws Exception {
        ObjetoRequest cambio = new ObjetoRequest(categoria.getId(), perdido.getNombre(), "Descripción editada",
                perdido.getFechaObjeto(), TipoObjeto.PERDIDO, perdido.getUbicacion(), null, null);
        simultaneos(() -> { gestion.confirmar(pareja(), ana.getId()); return true; },
                () -> { publicaciones.actualizar(perdido.getId(), cambio, ana.getId()); return true; });
        Objeto actual = objetos.findById(perdido.getId()).orElseThrow();
        assertThat(actual.getEstado()).isEqualTo(EstadoObjeto.RECUPERADO);
        assertThat(actual.getFechaRecuperacion()).isNotNull(); assertThat(actual.getDescripcion()).isEqualTo("Descripción editada");
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void falloDeBaseTrasGuardarPerdidoRevierteTodaLaOperacion(boolean filaPrevia) throws Exception {
        Coincidencia anterior = filaPrevia ? fila(encontrado, EstadoCoincidencia.REVISADA) : null;
        var fechaAnterior = objetos.findById(perdido.getId()).orElseThrow().getFechaActualizacion();
        jdbc.execute("ALTER TABLE coincidencias ADD CONSTRAINT qa_h22_impedir_aceptacion CHECK (estado <> 'ACEPTADA')");
        try {
            api.perform(post(ruta()).with(como(ana)).contentType(MediaType.APPLICATION_JSON).content(CONFIRMACION))
                    .andExpect(status().isInternalServerError())
                    .andExpect(jsonPath("$.mensaje").value("Error interno del servidor"));
            Objeto actual = objetos.findById(perdido.getId()).orElseThrow();
            assertThat(actual.getEstado()).isEqualTo(EstadoObjeto.ACTIVO); assertThat(actual.getFechaRecuperacion()).isNull();
            assertThat(actual.getFechaActualizacion()).isEqualTo(fechaAnterior);
            assertThat(coincidencias.count()).isEqualTo(filaPrevia ? 1 : 0);
            if (anterior != null) assertThat(coincidencias.findById(anterior.getId()).orElseThrow().getEstado()).isEqualTo(EstadoCoincidencia.REVISADA);
            assertThat(contactos.count()).isZero(); assertThat(notificaciones.count()).isZero();
        } finally {
            try (var conexion = jdbc.getDataSource().getConnection()) {
                boolean mysql = conexion.getMetaData().getDatabaseProductName().equals("MySQL");
                jdbc.execute("ALTER TABLE coincidencias DROP " + (mysql ? "CHECK" : "CONSTRAINT") + " qa_h22_impedir_aceptacion");
            }
        }
        assertThat(gestion.confirmar(pareja(), ana.getId()).estado()).isEqualTo("ACEPTADA");
    }
}
