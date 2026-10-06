package com.proyectointegrador.controller;

import com.proyectointegrador.dto.CategoriaResumen;
import com.proyectointegrador.dto.ObjetoRequest;
import com.proyectointegrador.dto.ObjetoResponse;
import com.proyectointegrador.dto.UsuarioResumen;
import com.proyectointegrador.entity.Rol;
import com.proyectointegrador.exception.GlobalExceptionHandler;
import com.proyectointegrador.security.UserPrincipal;
import com.proyectointegrador.service.ObjetoService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("H1 - Endpoint POST /api/objetos")
class ObjetoControllerTest {

    private ObjetoService objetoService;
    private MockMvc mockMvc;

    private static final String PUBLICACION_VALIDA = """
            {"categoriaId":3,"nombre":"Mochila negra","descripcion":"Mochila con libros de cálculo",
             "fechaObjeto":"2026-10-02","tipo":"ENCONTRADO","ubicacion":"Comedor principal",
             "latitud":19.43261,"longitud":-99.1332}""";

    @BeforeEach
    void setUp() {
        objetoService = mock(ObjetoService.class);
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new ObjetoController(objetoService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
        UserPrincipal usuario = new UserPrincipal(1L, "Ana", "ana@foundia.dev", "sin-password", Rol.USUARIO);
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(usuario, null, usuario.getAuthorities()));
    }

    @AfterEach
    void limpiar() {
        SecurityContextHolder.clearContext();
    }

    private ObjetoResponse respuesta() {
        return new ObjetoResponse(
                10L, "Mochila negra", "Mochila con libros de cálculo", "Comedor principal",
                new BigDecimal("19.43261"), new BigDecimal("-99.1332"),
                LocalDate.of(2026, 10, 2), "ENCONTRADO", "ACTIVO", LocalDateTime.of(2026, 10, 2, 12, 0),
                new CategoriaResumen(3L, "Mochila"), new UsuarioResumen(1L, "Ana", "Pérez"), List.of());
    }

    @Test
    @DisplayName("POST /api/objetos válido -> 201")
    void crearValido() throws Exception {
        when(objetoService.crear(any(ObjetoRequest.class), eq(1L))).thenReturn(respuesta());

        mockMvc.perform(post("/api/objetos").contentType(MediaType.APPLICATION_JSON).content(PUBLICACION_VALIDA))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.nombre").value("Mochila negra"))
                .andExpect(jsonPath("$.estado").value("ACTIVO"))
                .andExpect(jsonPath("$.categoria.nombre").value("Mochila"));
    }

    @Test
    @DisplayName("POST /api/objetos sin nombre -> 400 y no llama al servicio")
    void crearSinNombre() throws Exception {
        String body = """
                {"categoriaId":3,"nombre":"   ","descripcion":"Algo","fechaObjeto":"2026-10-02","tipo":"ENCONTRADO"}""";

        mockMvc.perform(post("/api/objetos").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje", containsString("nombre")));
        verify(objetoService, never()).crear(any(), any());
    }

    @Test
    @DisplayName("POST /api/objetos sin descripción -> 400")
    void crearSinDescripcion() throws Exception {
        String body = """
                {"categoriaId":3,"nombre":"Mochila","descripcion":"","fechaObjeto":"2026-10-02","tipo":"ENCONTRADO"}""";

        mockMvc.perform(post("/api/objetos").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje", containsString("descripcion")));
        verify(objetoService, never()).crear(any(), any());
    }

    @Test
    @DisplayName("POST /api/objetos sin categoría -> 400")
    void crearSinCategoria() throws Exception {
        String body = """
                {"nombre":"Mochila","descripcion":"Algo","fechaObjeto":"2026-10-02","tipo":"ENCONTRADO"}""";

        mockMvc.perform(post("/api/objetos").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje", containsString("categoriaId")));
        verify(objetoService, never()).crear(any(), any());
    }

    @Test
    @DisplayName("H7 - GET /api/objetos/categoria/{id} -> 200 con la lista filtrada")
    void porCategoria() throws Exception {
        when(objetoService.porCategoria(3L)).thenReturn(List.of(respuesta()));

        mockMvc.perform(get("/api/objetos/categoria/3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].categoria.id").value(3))
                .andExpect(jsonPath("$[0].categoria.nombre").value("Mochila"));
        verify(objetoService).porCategoria(3L);
    }

    @Test
    @DisplayName("H7 - GET /api/objetos/buscar?categoriaId=3 -> 200 pasando el filtro")
    void buscarPorCategoria() throws Exception {
        when(objetoService.buscar(null, null, 3L)).thenReturn(List.of(respuesta()));

        mockMvc.perform(get("/api/objetos/buscar").param("categoriaId", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nombre").value("Mochila negra"));
        verify(objetoService).buscar(null, null, 3L);
    }

    @Test
    @DisplayName("H7 - GET /api/objetos?categoriaId=3 -> 200 pasando el filtro al listado")
    void listarPorCategoria() throws Exception {
        when(objetoService.listar(null, null, 3L)).thenReturn(List.of(respuesta()));

        mockMvc.perform(get("/api/objetos").param("categoriaId", "3"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].categoria.id").value(3));
        verify(objetoService).listar(null, null, 3L);
    }

    @Test
    @DisplayName("H4 - POST /api/objetos con fecha futura -> 400")
    void crearConFechaFutura() throws Exception {
        String fechaFutura = java.time.LocalDate.now().plusDays(3).toString();
        String body = """
                {"categoriaId":3,"nombre":"Mochila","descripcion":"Algo","fechaObjeto":"%s","tipo":"ENCONTRADO"}"""
                .formatted(fechaFutura);

        mockMvc.perform(post("/api/objetos").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje", containsString("fecha")));
        verify(objetoService, never()).crear(any(), any());
    }
}
