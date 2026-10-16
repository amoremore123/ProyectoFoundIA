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
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
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
                {"categoriaId":3,"nombre":"   ","descripcion":"Algo","fechaObjeto":"2026-10-02","tipo":"ENCONTRADO","ubicacion":"Comedor"}""";

        mockMvc.perform(post("/api/objetos").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje", containsString("nombre")));
        verify(objetoService, never()).crear(any(), any());
    }

    @Test
    @DisplayName("H1 - POST /api/objetos con nombre de 151 caracteres -> 400, no 500")
    void crearConNombreLargo() throws Exception {
        String body = PUBLICACION_VALIDA.replace("Mochila negra", "N".repeat(151));
        mockMvc.perform(post("/api/objetos").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje", containsString("150")));
        verify(objetoService, never()).crear(any(), any());
    }

    @Test
    @DisplayName("H1 - POST acepta nombre de 150 y rechaza 151 caracteres antes de guardar")
    void limitesNombreEnApi() throws Exception {
        when(objetoService.crear(any(ObjetoRequest.class), eq(1L))).thenReturn(respuesta());
        String enLimite = PUBLICACION_VALIDA.replace("Mochila negra", "N".repeat(150));
        mockMvc.perform(post("/api/objetos").contentType(MediaType.APPLICATION_JSON).content(enLimite))
                .andExpect(status().isCreated());

        String excedido = PUBLICACION_VALIDA.replace("Mochila negra", "N".repeat(151));
        mockMvc.perform(post("/api/objetos").contentType(MediaType.APPLICATION_JSON).content(excedido))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje", containsString("nombre")));
        verify(objetoService).crear(any(ObjetoRequest.class), eq(1L));
    }

    @Test
    @DisplayName("H1 - API valida descripción por bytes UTF-8 y no intenta guardar si supera 16000")
    void limitesDescripcionEnApi() throws Exception {
        when(objetoService.crear(any(ObjetoRequest.class), eq(1L))).thenReturn(respuesta());
        List<String> aceptadas = List.of(
                "A".repeat(15999),
                "A".repeat(16000),
                "é".repeat(16000),
                "😀".repeat(8000));
        for (String descripcion : aceptadas) {
            assertThatUtf8NoSuperaText(descripcion);
            String body = PUBLICACION_VALIDA.replace("Mochila con libros de cálculo", descripcion);
            mockMvc.perform(post("/api/objetos").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isCreated());
        }

        for (String descripcion : List.of("A".repeat(16001), "A".repeat(70000))) {
            String body = PUBLICACION_VALIDA.replace("Mochila con libros de cálculo", descripcion);
            mockMvc.perform(post("/api/objetos").contentType(MediaType.APPLICATION_JSON).content(body))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.mensaje", containsString("descripcion")));
        }
        verify(objetoService, org.mockito.Mockito.times(4)).crear(any(ObjetoRequest.class), eq(1L));
    }

    private void assertThatUtf8NoSuperaText(String texto) {
        org.assertj.core.api.Assertions.assertThat(texto.getBytes(StandardCharsets.UTF_8).length)
                .isLessThanOrEqualTo(65535);
    }

    @ParameterizedTest
    @ValueSource(strings = {"null", "\"\"", "\"   \""})
    @DisplayName("H3 - POST /api/objetos sin ubicación -> 400")
    void crearSinUbicacion(String ubicacionJson) throws Exception {
        String body = PUBLICACION_VALIDA.replace("\"Comedor principal\"", ubicacionJson);
        mockMvc.perform(post("/api/objetos").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje", containsString("ubicacion")));
        verify(objetoService, never()).crear(any(), any());
    }

    @Test
    @DisplayName("H3 - POST /api/objetos con ubicación de 256 caracteres -> 400")
    void crearConUbicacionLarga() throws Exception {
        String body = PUBLICACION_VALIDA.replace("Comedor principal", "U".repeat(256));
        mockMvc.perform(post("/api/objetos").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje", containsString("255")));
        verify(objetoService, never()).crear(any(), any());
    }

    @Test
    @DisplayName("H3 - API acepta ubicación de 255 caracteres con tildes y coordenadas vacías")
    void crearConUbicacionEnLimiteYCoordenadasVacias() throws Exception {
        when(objetoService.crear(any(ObjetoRequest.class), eq(1L))).thenReturn(respuesta());
        String prefijo = "Laboratorio de cómputo ñ";
        String ubicacion = prefijo + "U".repeat(255 - prefijo.length());
        String body = PUBLICACION_VALIDA
                .replace("Comedor principal", ubicacion)
                .replace("19.43261", "null")
                .replace("-99.1332", "null");

        mockMvc.perform(post("/api/objetos").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());

        ArgumentCaptor<ObjetoRequest> captor = ArgumentCaptor.forClass(ObjetoRequest.class);
        verify(objetoService).crear(captor.capture(), eq(1L));
        org.assertj.core.api.Assertions.assertThat(captor.getValue().ubicacion()).isEqualTo(ubicacion);
        org.assertj.core.api.Assertions.assertThat(captor.getValue().latitud()).isNull();
        org.assertj.core.api.Assertions.assertThat(captor.getValue().longitud()).isNull();
    }

    @ParameterizedTest
    @CsvSource({"90, 180", "-90, -180", "0, 0"})
    @DisplayName("H3 - API permite los extremos de latitud y longitud")
    void coordenadasEnLimiteApi(String latitud, String longitud) throws Exception {
        when(objetoService.crear(any(ObjetoRequest.class), eq(1L))).thenReturn(respuesta());
        String body = PUBLICACION_VALIDA.replace("19.43261", latitud).replace("-99.1332", longitud);

        mockMvc.perform(post("/api/objetos").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        verify(objetoService).crear(any(ObjetoRequest.class), eq(1L));
    }

    @ParameterizedTest
    @CsvSource({"91, -99.1332, latitud", "-91, -99.1332, latitud",
            "19.43261, 181, longitud", "19.43261, -181, longitud"})
    @DisplayName("H3 - POST /api/objetos con coordenadas fuera de rango -> 400, no 500")
    void crearConCoordenadasFueraDeRango(String latitud, String longitud, String campo) throws Exception {
        String body = PUBLICACION_VALIDA.replace("19.43261", latitud).replace("-99.1332", longitud);
        mockMvc.perform(post("/api/objetos").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje", containsString(campo)));
        verify(objetoService, never()).crear(any(), any());
    }

    @Test
    @DisplayName("POST /api/objetos sin descripción -> 400")
    void crearSinDescripcion() throws Exception {
        String body = """
                {"categoriaId":3,"nombre":"Mochila","descripcion":"","fechaObjeto":"2026-10-02","tipo":"ENCONTRADO","ubicacion":"Comedor"}""";

        mockMvc.perform(post("/api/objetos").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje", containsString("descripcion")));
        verify(objetoService, never()).crear(any(), any());
    }

    @Test
    @DisplayName("POST /api/objetos sin categoría -> 400")
    void crearSinCategoria() throws Exception {
        String body = """
                {"nombre":"Mochila","descripcion":"Algo","fechaObjeto":"2026-10-02","tipo":"ENCONTRADO","ubicacion":"Comedor"}""";

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
                {"categoriaId":3,"nombre":"Mochila","descripcion":"Algo","fechaObjeto":"%s","tipo":"ENCONTRADO","ubicacion":"Comedor"}"""
                .formatted(fechaFutura);

        mockMvc.perform(post("/api/objetos").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje", containsString("fecha")));
        verify(objetoService, never()).crear(any(), any());
    }

    @Test
    @DisplayName("H4 - POST acepta la fecha de hoy en formato ISO")
    void crearConFechaDeHoy() throws Exception {
        when(objetoService.crear(any(ObjetoRequest.class), eq(1L))).thenReturn(respuesta());
        String body = PUBLICACION_VALIDA.replace("2026-10-02", LocalDate.now().toString());

        mockMvc.perform(post("/api/objetos").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isCreated());
        verify(objetoService).crear(any(ObjetoRequest.class), eq(1L));
    }

    @Test
    @DisplayName("H4 - POST sin fecha devuelve 400 con error de fechaObjeto antes de guardar")
    void crearSinFecha() throws Exception {
        String body = PUBLICACION_VALIDA.replace("\"fechaObjeto\":\"2026-10-02\",", "");

        mockMvc.perform(post("/api/objetos").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje", containsString("fechaObjeto")));
        verify(objetoService, never()).crear(any(), any());
    }

    @ParameterizedTest
    @ValueSource(strings = {"16/10/2026", "2026-02-30"})
    @DisplayName("H4 - POST rechaza fechas mal formadas con error de campo y sin guardar")
    void crearConFormatoFechaInvalido(String fecha) throws Exception {
        String body = PUBLICACION_VALIDA.replace("2026-10-02", fecha);

        mockMvc.perform(post("/api/objetos").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje", containsString("fechaObjeto")));
        verify(objetoService, never()).crear(any(), any());
    }
}
