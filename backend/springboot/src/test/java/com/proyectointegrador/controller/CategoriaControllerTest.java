package com.proyectointegrador.controller;

import com.proyectointegrador.dto.CategoriaResponse;
import com.proyectointegrador.exception.GlobalExceptionHandler;
import com.proyectointegrador.service.CategoriaService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("H7 - Contrato y recuperación del catálogo")
class CategoriaControllerTest {

    private CategoriaService categoriaService;
    private MockMvc mockMvc;

    private List<CategoriaResponse> categorias() {
        return List.of(
                new CategoriaResponse(205L, "Celular", "Teléfonos", true),
                new CategoriaResponse(309L, "Documentos antiguos", "Histórico", false),
                new CategoriaResponse(101L, "Mochila", "Bolsos", true));
    }

    @BeforeEach
    void setUp() {
        categoriaService = mock(CategoriaService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new CategoriaController(categoriaService))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    @Test
    @DisplayName("Ordenar el catálogo no renumera IDs ni pierde el estado desactivado")
    void conservaIdsYEstado() throws Exception {
        when(categoriaService.listar()).thenReturn(categorias());
        mockMvc.perform(get("/api/categorias"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0].id").value(205))
                .andExpect(jsonPath("$[1].id").value(309))
                .andExpect(jsonPath("$[1].estado").value(false))
                .andExpect(jsonPath("$[2].id").value(101));
    }

    @Test
    @DisplayName("El catálogo realmente vacío devuelve 200 y una lista vacía")
    void catalogoVacio() throws Exception {
        when(categoriaService.listar()).thenReturn(List.of());
        mockMvc.perform(get("/api/categorias"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @DisplayName("Un fallo devuelve 500 en español; el siguiente intento recupera el catálogo")
    void falloYRecuperacion() throws Exception {
        when(categoriaService.listar()).thenThrow(new IllegalStateException("Detalle interno de QA"))
                .thenReturn(categorias());
        mockMvc.perform(get("/api/categorias"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.mensaje").value("Error interno del servidor"));
        mockMvc.perform(get("/api/categorias"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3));
    }
}
