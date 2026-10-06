package com.proyectointegrador.service;

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
        objeto.setFechaPublicacion(LocalDateTime.of(2026, 10, 5, 12, 0));
        entityManager.persist(objeto);
        return objeto;
    }

    private void sincronizar() {
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @DisplayName("Las consultas generales solo devuelven objetos públicos")
    void consultasPublicas() {
        for (List<ObjetoResponse> resultado : List.of(
                objetoService.listar(null, null, null), objetoService.buscar(null, null, null))) {
            assertThat(resultado).extracting(ObjetoResponse::id)
                    .containsExactlyInAnyOrder(historico.getId(), recuperado.getId(), celular.getId(), mochila.getId());
        }
    }

    @Test
    @DisplayName("H7 - El endpoint dedicado excluye ocultos y eliminados")
    void porCategoria() {
        assertThat(objetoService.porCategoria(mochilas.getId()))
                .extracting(ObjetoResponse::id).containsExactlyInAnyOrder(recuperado.getId(), mochila.getId());
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
        assertThat(objetoService.porUbicacion("BIBLIOTECA"))
                .extracting(ObjetoResponse::id).containsExactly(mochila.getId());
        assertThat(objetoService.porFecha(LocalDate.now().minusDays(2), LocalDate.now()))
                .extracting(ObjetoResponse::id)
                .containsExactlyInAnyOrder(historico.getId(), recuperado.getId(), celular.getId(), mochila.getId());
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
    @DisplayName("La búsqueda HTTP anónima solo devuelve publicaciones públicas")
    void endpointPublico() throws Exception {
        mockMvc.perform(get("/api/objetos/buscar"))
                .andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(4));
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
