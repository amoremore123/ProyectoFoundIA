package com.proyectointegrador.controller;

import com.proyectointegrador.dto.AsistenteResponse;
import com.proyectointegrador.security.AsistenteRateLimiter;

import com.proyectointegrador.exception.AsistenteNoDisponibleException;
import com.proyectointegrador.exception.GlobalExceptionHandler;
import com.proyectointegrador.exception.LimiteAsistenteException;
import com.proyectointegrador.service.AsistenteService;
import com.proyectointegrador.service.VozService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("Fase 5 - Contrato de POST /api/asistente/interpretar")
class AsistenteControllerTest {

    private AsistenteService servicio;
    private VozService voz;
    private AsistenteRateLimiter limite;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        servicio = mock(AsistenteService.class);
        voz = mock(VozService.class);
        limite = mock(AsistenteRateLimiter.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new AsistenteController(servicio, limite, mock(com.proyectointegrador.service.SugerenciaPublicacionService.class)), new VozController(voz))
                .setControllerAdvice(new GlobalExceptionHandler()).build();
    }

    private static String cuerpo(String mensaje) {
        return "{\"mensaje\":\"" + mensaje + "\"}";
    }

    @Test
    @DisplayName("Devuelve la interpretación con la forma que espera la web")
    void interpretacion() throws Exception {
        when(servicio.interpretar(eq("perdí mi celular"), isNull())).thenReturn(new AsistenteResponse(
                "buscar", "ENCONTRADO", new AsistenteResponse.CategoriaAsistente(1L, "Celular"), "biblioteca", List.of("celular"), List.of(), "ia", null, null));

        mockMvc.perform(post("/api/asistente/interpretar").contentType(MediaType.APPLICATION_JSON).content(cuerpo("perdí mi celular")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.intencion").value("buscar"))
                .andExpect(jsonPath("$.tipoBuscado").value("ENCONTRADO"))
                .andExpect(jsonPath("$.categoria.id").value(1))
                .andExpect(jsonPath("$.palabras[0]").value("celular"));
        // Sin sesión, el límite se cuenta por IP
        verify(servicio).interpretar(eq("perdí mi celular"), isNull());
        verify(limite).verificar("ip:127.0.0.1");
    }

    @Test
    @DisplayName("Rechaza mensajes vacíos o de más de 300 caracteres sin llamar a la IA")
    void validaMensaje() throws Exception {
        mockMvc.perform(post("/api/asistente/interpretar").contentType(MediaType.APPLICATION_JSON).content(cuerpo(" ")))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/api/asistente/interpretar").contentType(MediaType.APPLICATION_JSON).content(cuerpo("a".repeat(301))))
                .andExpect(status().isBadRequest());
        verify(servicio, never()).interpretar(anyString(), isNull());
    }

    @Test
    @DisplayName("Responde 503 cuando la IA no está disponible, para que la web use el motor local")
    void noDisponible() throws Exception {
        when(servicio.interpretar(anyString(), isNull())).thenThrow(new AsistenteNoDisponibleException("La IA de FINDI no está configurada en el servidor."));
        mockMvc.perform(post("/api/asistente/interpretar").contentType(MediaType.APPLICATION_JSON).content(cuerpo("mochila")))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.mensaje").value("La IA de FINDI no está configurada en el servidor."));
    }

    @Test
    @DisplayName("Responde 429 con Retry-After al superar el límite")
    void limite() throws Exception {
        doThrow(new LimiteAsistenteException(42)).when(limite).verificar("ip:127.0.0.1");
        mockMvc.perform(post("/api/asistente/interpretar").contentType(MediaType.APPLICATION_JSON).content(cuerpo("mochila")))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().string("Retry-After", "42"));
    }

    @Test
    @DisplayName("Fase 6 - /voz devuelve el audio MP3 de la respuesta de FINDI")
    void voz() throws Exception {
        byte[] audio = {1, 2, 3};
        when(servicio.interpretar(anyString(), isNull())).thenReturn(null);
        when(voz.sintetizar(eq("Hola, soy FINDI"), eq("ip:127.0.0.1"))).thenReturn(audio);

        mockMvc.perform(post("/api/asistente/voz").contentType(MediaType.APPLICATION_JSON).content("{\"texto\":\"Hola, soy FINDI\"}"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "audio/mpeg"))
                .andExpect(content().bytes(audio));
    }

    @Test
    @DisplayName("Fase 6 - /voz rechaza textos vacíos o de más de 600 caracteres y responde 503 sin voz configurada")
    void vozErrores() throws Exception {
        mockMvc.perform(post("/api/asistente/voz").contentType(MediaType.APPLICATION_JSON).content("{\"texto\":\"" + "a".repeat(601) + "\"}"))
                .andExpect(status().isBadRequest());
        verify(voz, never()).sintetizar(anyString(), anyString());

        when(voz.sintetizar(anyString(), anyString())).thenThrow(new AsistenteNoDisponibleException("La voz natural de FINDI no está configurada en el servidor."));
        mockMvc.perform(post("/api/asistente/voz").contentType(MediaType.APPLICATION_JSON).content("{\"texto\":\"Hola\"}"))
                .andExpect(status().isServiceUnavailable());
    }
}
