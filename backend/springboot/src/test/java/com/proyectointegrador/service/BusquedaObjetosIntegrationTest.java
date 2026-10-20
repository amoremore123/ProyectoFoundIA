package com.proyectointegrador.service;

import com.proyectointegrador.dto.FiltroBusqueda;
import com.proyectointegrador.dto.ObjetoResponse;
import com.proyectointegrador.entity.Categoria;
import com.proyectointegrador.entity.EstadoObjeto;
import com.proyectointegrador.entity.Objeto;
import com.proyectointegrador.entity.TipoObjeto;
import com.proyectointegrador.entity.Usuario;
import com.proyectointegrador.exception.ResourceNotFoundException;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("H6/H7 - Consultas reales y endpoints públicos con base temporal")
@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:foundia_busqueda_test;MODE=MySQL;DB_CLOSE_DELAY=-1",
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
class BusquedaObjetosIntegrationTest {
    @Autowired
    private BusquedaService busquedaService;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private ObjetoService objetoService;
    @Autowired
    private MockMvc mockMvc;

    private Usuario usuario;
    private Categoria mochilas;
    private Categoria celulares;
    private Categoria antigua;
    private Objeto mochila;
    private Objeto celular;
    private Objeto recuperado;
    private Objeto historico;
    private Objeto oculto;
    private Objeto eliminado;

    @BeforeEach
    void prepararDatos() {
        usuario = new Usuario();
        usuario.setNombre("Ana");
        usuario.setApellido("Prueba");
        usuario.setCorreo("busqueda@prueba.invalid");
        usuario.setPassword("no-se-utiliza-para-login");
        usuario.setVerificado(true);
        entityManager.persist(usuario);
        mochilas = categoria("Mochila", true);
        celulares = categoria("Celular", true);
        antigua = categoria("Documentos antiguos", false);
        mochila = objeto("Mochila negra", "Cuaderno de cálculo", "Biblioteca central",
                mochilas, TipoObjeto.PERDIDO, EstadoObjeto.ACTIVO);
        celular = objeto("Celular azul", "Funda de tela", "Comedor",
                celulares, TipoObjeto.PERDIDO, EstadoObjeto.ACTIVO);
        recuperado = objeto("Mochila recuperada", "Llavero rojo", "Aula 204",
                mochilas, TipoObjeto.ENCONTRADO, EstadoObjeto.RECUPERADO);
        historico = objeto("Carnet antiguo", "Tarjeta de acceso", "Taller",
                antigua, TipoObjeto.PERDIDO, EstadoObjeto.ACTIVO);
        oculto = objeto("Mochila secreta", "Cuaderno de cálculo", "Biblioteca central",
                mochilas, TipoObjeto.PERDIDO, EstadoObjeto.OCULTO);
        eliminado = objeto("Mochila borrada", "Cuaderno de cálculo", "Biblioteca central",
                mochilas, TipoObjeto.PERDIDO, EstadoObjeto.ELIMINADO);
        sincronizar();
    }

    private Categoria categoria(String nombre, boolean activa) {
        Categoria categoria = new Categoria();
        categoria.setNombre(nombre);
        categoria.setEstado(activa);
        entityManager.persist(categoria);
        return categoria;
    }

    private Objeto objeto(String nombre, String descripcion, String ubicacion,
                          Categoria categoria, TipoObjeto tipo, EstadoObjeto estado) {
        Objeto objeto = new Objeto();
        objeto.setUsuario(usuario);
        objeto.setCategoria(categoria);
        objeto.setNombre(nombre);
        objeto.setDescripcion(descripcion);
        objeto.setUbicacion(ubicacion);
        objeto.setTipo(tipo);
        objeto.setEstado(estado);
        objeto.setFechaObjeto(LocalDate.now().minusDays(1));
        // Misma fecha: el id debe desempatar el orden de forma estable.
        objeto.setFechaPublicacion(LocalDateTime.of(2026, 10, 5, 12, 0));
        entityManager.persist(objeto);
        return objeto;
    }

    private void sincronizar() {
        entityManager.flush();
        entityManager.clear();
    }

    private List<ObjetoResponse> buscar(String texto, TipoObjeto tipo, Long categoriaId) {
        return busquedaService.buscar(new FiltroBusqueda(texto, tipo, categoriaId, null, null, null,
                null, null, null));
    }

    @ParameterizedTest
    @ValueSource(strings = {"negra", "cuaderno", "biblioteca", "  NEGRA  "})
    @DisplayName("H6 - Busca por nombre, descripción o ubicación sin distinguir mayúsculas")
    void busquedaTextual(String texto) {
        assertThat(buscar(texto, null, null))
                .extracting(ObjetoResponse::id).containsExactly(mochila.getId());
    }

    @ParameterizedTest
    @ValueSource(strings = {"neG", "CUADER", "BIBLIO", "  NEGRA  ", "CÁLCULO"})
    @DisplayName("H6 - HTTP conserva coincidencias parciales y textos UTF-8 de los tres campos")
    void fragmentosPorHttp(String texto) throws Exception {
        mockMvc.perform(get("/api/objetos/buscar").param("q", texto))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(mochila.getId()))
                .andExpect(jsonPath("$[0].descripcion").value("Cuaderno de cálculo"))
                .andExpect(jsonPath("$[0].ubicacion").value("Biblioteca central"));
    }

    @ParameterizedTest
    @ValueSource(strings = {"%", "_", "!", "!%_", "\\", "A+B", "O'Neil"})
    @DisplayName("H6 - Los literales en nombre, descripción y ubicación no actúan como comodines")
    void literalesPorHttp(String texto) throws Exception {
        usuario = entityManager.getReference(Usuario.class, usuario.getId());
        Categoria categoria = entityManager.getReference(Categoria.class, celulares.getId());
        Objeto nombre = objeto("Etiqueta " + texto, "Detalle", "Laboratorio",
                categoria, TipoObjeto.PERDIDO, EstadoObjeto.ACTIVO);
        Objeto descripcion = objeto("Descripción literal", "Detalle " + texto, "Laboratorio",
                categoria, TipoObjeto.PERDIDO, EstadoObjeto.ACTIVO);
        Objeto ubicacion = objeto("Ubicación literal", "Detalle", "Sector " + texto,
                categoria, TipoObjeto.PERDIDO, EstadoObjeto.ACTIVO);
        objeto("Etiqueta 1000 A1B Avisoo A B ONeil", "Sin el literal", "Otro sector",
                categoria, TipoObjeto.PERDIDO, EstadoObjeto.ACTIVO);
        sincronizar();

        mockMvc.perform(get("/api/objetos/buscar").param("q", texto))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].id").value(ubicacion.getId()))
                .andExpect(jsonPath("$[1].id").value(descripcion.getId()))
                .andExpect(jsonPath("$[2].id").value(nombre.getId()));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   ", "\t"})
    @DisplayName("H6 - q ausente o blanco conserva la lista pública y su orden también por HTTP")
    void sinTextoPorHttp(String texto) throws Exception {
        var solicitud = get("/api/objetos/buscar");
        if (texto != null) solicitud.param("q", texto);
        mockMvc.perform(solicitud)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[0].id").value(historico.getId()))
                .andExpect(jsonPath("$[1].id").value(recuperado.getId()))
                .andExpect(jsonPath("$[2].id").value(celular.getId()))
                .andExpect(jsonPath("$[3].id").value(mochila.getId()));
    }

    @Test
    @DisplayName("H6 - La fecha tiene prioridad sobre el id y el id solo desempata")
    void ordenPorFechaAntesDeId() throws Exception {
        entityManager.createNativeQuery("update objetos set fecha_publicacion = :fecha where id = :id")
                .setParameter("fecha", LocalDateTime.of(2026, 10, 6, 12, 0))
                .setParameter("id", mochila.getId()).executeUpdate();
        sincronizar();
        mockMvc.perform(get("/api/objetos/buscar"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[0].id").value(mochila.getId()))
                .andExpect(jsonPath("$[1].id").value(historico.getId()))
                .andExpect(jsonPath("$[2].id").value(recuperado.getId()))
                .andExpect(jsonPath("$[3].id").value(celular.getId()));
    }

    @Test
    @DisplayName("H6 - Error de entrada, vacío y recuperación son respuestas diferentes")
    void errorVacioYRecuperacion() throws Exception {
        mockMvc.perform(get("/api/objetos/buscar").param("tipo", "OTRO"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje", containsString("tipo")));
        mockMvc.perform(get("/api/objetos/buscar").param("q", "no-existe-987"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mockMvc.perform(get("/api/objetos/buscar").param("q", "negra"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {"", "   ", "\t"})
    @DisplayName("H6 - Sin texto devuelve solo objetos públicos, con orden estable")
    void sinTexto(String texto) {
        assertThat(buscar(texto, null, null))
                .extracting(ObjetoResponse::id)
                .containsExactly(historico.getId(), recuperado.getId(), celular.getId(), mochila.getId());
    }

    @ParameterizedTest
    @ValueSource(strings = {"100%", "A_B", "Aviso!"})
    @DisplayName("H6 - Los caracteres de LIKE se buscan literalmente")
    void comodinesLiterales(String texto) {
        // Los fixtures están detached tras clear(); usamos referencias administradas.
        usuario = entityManager.getReference(Usuario.class, usuario.getId());
        Categoria categoria = entityManager.getReference(Categoria.class, celulares.getId());
        Objeto literal = objeto("Etiqueta " + texto, "Detalle", "Laboratorio",
                categoria, TipoObjeto.PERDIDO, EstadoObjeto.ACTIVO);
        objeto("Etiqueta 1000 A1B Avisoo", "Detalle", "Laboratorio",
                categoria, TipoObjeto.PERDIDO, EstadoObjeto.ACTIVO);
        sincronizar();

        assertThat(buscar(texto, null, null))
                .extracting(ObjetoResponse::id).containsExactly(literal.getId());
    }

    @Test
    @DisplayName("H6 - Un texto sin coincidencias devuelve una lista vacía")
    void sinResultados() {
        assertThat(buscar("no-existe-987", null, null)).isEmpty();
    }

    @Test
    @DisplayName("H6/H7 - Texto, tipo y categoría se combinan, no se suman")
    void filtrosCombinados() {
        assertThat(buscar("mochila", TipoObjeto.ENCONTRADO, mochilas.getId()))
                .extracting(ObjetoResponse::id).containsExactly(recuperado.getId());
        assertThat(buscar("mochila", TipoObjeto.PERDIDO, celulares.getId())).isEmpty();
    }

    @Test
    @DisplayName("H7 - Desactivar una categoría conserva el catálogo y sus publicaciones anteriores")
    void categoriaDesactivada() throws Exception {
        assertThat(buscar(null, null, antigua.getId()))
                .extracting(ObjetoResponse::id).containsExactly(historico.getId());
        assertThat(objetoService.porCategoria(antigua.getId()))
                .extracting(ObjetoResponse::id).containsExactly(historico.getId());
        mockMvc.perform(get("/api/categorias"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + antigua.getId() + ")].estado").value(hasItem(false)));
    }

    @Test
    @DisplayName("El catálogo para el filtro entrega categorías en orden alfabético")
    void categoriasEnOrdenAlfabetico() throws Exception {
        mockMvc.perform(get("/api/categorias"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nombre").value("Celular"))
                .andExpect(jsonPath("$[1].nombre").value("Documentos antiguos"))
                .andExpect(jsonPath("$[2].nombre").value("Mochila"));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @DisplayName("H7 - Una categoría vacía activa o desactivada se conserva y devuelve 200 en los tres filtros")
    void categoriaVaciaEnLosTresFiltros(boolean activa) throws Exception {
        Categoria vacia = categoria("Sin publicaciones " + activa, activa);
        sincronizar();
        for (String ruta : List.of("/api/objetos?categoriaId=" + vacia.getId(),
                "/api/objetos/buscar?categoriaId=" + vacia.getId(),
                "/api/objetos/categoria/" + vacia.getId())) {
            mockMvc.perform(get(ruta))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(0));
        }
        mockMvc.perform(get("/api/categorias"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == " + vacia.getId() + ")].estado").value(hasItem(activa)));
    }

    @ParameterizedTest
    @ValueSource(strings = {"mochilas", "celulares", "antigua"})
    @DisplayName("H7 - Los tres filtros coinciden en objetos, orden y relación de categoría")
    void relacionCategoriaEnLosTresFiltros(String caso) throws Exception {
        Categoria categoria = switch (caso) {
            case "mochilas" -> mochilas;
            case "celulares" -> celulares;
            default -> antigua;
        };
        List<Long> ids = switch (caso) {
            case "mochilas" -> List.of(recuperado.getId(), mochila.getId());
            case "celulares" -> List.of(celular.getId());
            default -> List.of(historico.getId());
        };
        for (String ruta : List.of("/api/objetos?categoriaId=" + categoria.getId(),
                "/api/objetos/buscar?categoriaId=" + categoria.getId(),
                "/api/objetos/categoria/" + categoria.getId())) {
            var resultado = mockMvc.perform(get(ruta))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.length()").value(ids.size()));
            for (int i = 0; i < ids.size(); i++) {
                resultado.andExpect(jsonPath("$[" + i + "].id").value(ids.get(i)))
                        .andExpect(jsonPath("$[" + i + "].categoria.id").value(categoria.getId()))
                        .andExpect(jsonPath("$[" + i + "].categoria.nombre").value(categoria.getNombre()));
            }
        }
    }

    @Test
    @DisplayName("H7 - El orden visual alfabético no cambia los IDs ni sus publicaciones")
    void ordenVisualNoCambiaIds() throws Exception {
        mockMvc.perform(get("/api/categorias"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(celulares.getId()))
                .andExpect(jsonPath("$[1].id").value(antigua.getId()))
                .andExpect(jsonPath("$[2].id").value(mochilas.getId()));
        mockMvc.perform(get("/api/objetos/buscar").param("q", "mochila")
                        .param("categoriaId", mochilas.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
        mockMvc.perform(get("/api/objetos/buscar").param("q", "mochila")
                        .param("categoriaId", celulares.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("H7 - Todas retira solo categoría y mantiene texto, tipo y privacidad")
    void todasConservaTextoTipoYVisibilidad() throws Exception {
        usuario = entityManager.getReference(Usuario.class, usuario.getId());
        Categoria categoria = entityManager.getReference(Categoria.class, celulares.getId());
        Objeto otra = objeto("Celular con funda", "Funda con dibujo de mochila", "Taller",
                categoria, TipoObjeto.PERDIDO, EstadoObjeto.ACTIVO);
        sincronizar();
        mockMvc.perform(get("/api/objetos/buscar").param("q", "MOCHILA").param("tipo", "PERDIDO")
                        .param("categoriaId", mochilas.getId().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(mochila.getId()));
        mockMvc.perform(get("/api/objetos/buscar").param("q", "MOCHILA").param("tipo", "PERDIDO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(otra.getId()))
                .andExpect(jsonPath("$[1].id").value(mochila.getId()));
    }

    @ParameterizedTest
    @ValueSource(strings = {"texto", "1.5", "9223372036854775808"})
    @DisplayName("H7 - Formatos inválidos y desbordamiento del ID devuelven 400, no 500")
    void formatoCategoriaEnLosTresFiltros(String id) throws Exception {
        for (String ruta : List.of("/api/objetos?categoriaId=" + id,
                "/api/objetos/buscar?categoriaId=" + id, "/api/objetos/categoria/" + id)) {
            mockMvc.perform(get(ruta))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.mensaje", containsString("Valor inválido para el parámetro")));
        }
    }

    @ParameterizedTest
    @ValueSource(longs = {0, -1, Long.MAX_VALUE})
    @DisplayName("H7 - Un ID inexistente devuelve 404 incluso sin coincidencias de texto y tipo")
    void inexistenteNoSeConfundeConVacia(Long id) throws Exception {
        for (String ruta : List.of("/api/objetos", "/api/objetos/buscar")) {
            mockMvc.perform(get(ruta).param("categoriaId", id.toString()).param("q", "no-existe-987")
                            .param("tipo", "ENCONTRADO"))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.mensaje", containsString("Categoría no encontrada")));
        }
        mockMvc.perform(get("/api/objetos/categoria/" + id))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("H7 - Una categoría inexistente tiene la misma regla en los tres filtros")
    void categoriaInexistente() {
        assertThatThrownBy(() -> buscar(null, null, Long.MAX_VALUE))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> objetoService.listar(null, null, Long.MAX_VALUE))
                .isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> objetoService.porCategoria(Long.MAX_VALUE))
                .isInstanceOf(ResourceNotFoundException.class);
    }

    @Test
    @DisplayName("H7 - Los tres endpoints informan 404 si la categoría no existe")
    void endpointsCategoriaInexistente() throws Exception {
        for (String ruta : List.of("/api/objetos", "/api/objetos/buscar")) {
            mockMvc.perform(get(ruta).param("categoriaId", Long.toString(Long.MAX_VALUE)))
                    .andExpect(status().isNotFound())
                    .andExpect(jsonPath("$.mensaje", containsString("Categoría no encontrada")));
        }
        mockMvc.perform(get("/api/objetos/categoria/" + Long.MAX_VALUE))
                .andExpect(status().isNotFound());
    }

    @Test
    @DisplayName("H6/H7 - Los parámetros con formato incorrecto devuelven 400, no 500")
    void parametrosInvalidos() throws Exception {
        mockMvc.perform(get("/api/objetos/buscar").param("categoriaId", "texto"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(get("/api/objetos/buscar").param("tipo", "OTRO"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("Las consultas generales solo devuelven objetos públicos")
    void consultasPublicas() {
        for (List<ObjetoResponse> resultado : List.of(
                objetoService.listar(null, null, null), buscar(null, null, null))) {
            assertThat(resultado).extracting(ObjetoResponse::id)
                    .containsExactlyInAnyOrder(historico.getId(), recuperado.getId(), celular.getId(), mochila.getId());
        }
    }

    @Test
    @DisplayName("H7 - El endpoint dedicado excluye ocultos y eliminados")
    void porCategoria() {
        assertThat(objetoService.porCategoria(mochilas.getId()))
                .extracting(ObjetoResponse::id).containsExactly(recuperado.getId(), mochila.getId());
    }

    @Test
    @DisplayName("Pedir el estado OCULTO no permite saltarse la visibilidad pública")
    void estadoOcultoNoPermiteAcceso() {
        assertThat(objetoService.listar(null, EstadoObjeto.OCULTO, null)).isEmpty();
        assertThat(objetoService.listar(null, EstadoObjeto.ELIMINADO, null)).isEmpty();
        assertThat(objetoService.listar(null, EstadoObjeto.ACTIVO, mochilas.getId()))
                .extracting(ObjetoResponse::id).containsExactly(mochila.getId());
    }

    @Test
    @DisplayName("Los otros filtros públicos tampoco revelan objetos ocultos")
    void otrosFiltrosPublicos() {
        assertThat(busquedaService.buscar(new FiltroBusqueda(null, null, null, "BIBLIOTECA",
                null, null, null, null, null)))
                .extracting(ObjetoResponse::id).containsExactly(mochila.getId());
        assertThat(busquedaService.porFecha(new FiltroBusqueda(null, null, null, null,
                LocalDate.now().minusDays(2), LocalDate.now(), null, null, null)))
                .extracting(ObjetoResponse::id)
                .containsExactly(historico.getId(), recuperado.getId(), celular.getId(), mochila.getId());
    }

    @Test
    @DisplayName("Detalle y coincidencias no permiten consultar ocultos ni eliminados")
    void detalleNoPublico() {
        for (Objeto objeto : List.of(oculto, eliminado)) {
            assertThatThrownBy(() -> objetoService.obtener(objeto.getId()))
                    .isInstanceOf(ResourceNotFoundException.class);
            assertThatThrownBy(() -> objetoService.coincidencias(objeto.getId()))
                    .isInstanceOf(ResourceNotFoundException.class);
        }
        assertThat(objetoService.obtener(recuperado.getId()).id()).isEqualTo(recuperado.getId());
    }

    @Test
    @DisplayName("La consulta privada del dueño conserva sus objetos no públicos")
    void conservaPublicacionesPropias() {
        assertThat(objetoService.porUsuario(usuario.getId()))
                .extracting(ObjetoResponse::id).contains(oculto.getId(), eliminado.getId());
    }

    @Test
    @DisplayName("H6/H7 - Búsqueda anónima con consulta SQL real")
    void endpointPublico() throws Exception {
        mockMvc.perform(get("/api/objetos/buscar").param("q", " MOCHILA ")
                        .param("categoriaId", mochilas.getId().toString()).param("tipo", "PERDIDO"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(mochila.getId()))
                .andExpect(jsonPath("$[0].categoria.id").value(mochilas.getId()));
    }

    @Test
    @DisplayName("Acceso HTTP anónimo a un objeto oculto devuelve 404")
    void endpointOculto() throws Exception {
        for (Objeto objeto : List.of(oculto, eliminado)) {
            mockMvc.perform(get("/api/objetos/" + objeto.getId())).andExpect(status().isNotFound());
            mockMvc.perform(get("/api/objetos/" + objeto.getId() + "/coincidencias"))
                    .andExpect(status().isNotFound());
        }
        mockMvc.perform(get("/api/objetos/categoria/" + mochilas.getId()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(2));
    }
}
