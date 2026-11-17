package com.proyectointegrador.service;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

// Un servidor local imita a Groq: no se llama al servicio real ni se usa una clave verdadera
@DisplayName("Fase 5 - Cliente compatible con OpenAI (Groq)")
class ClienteIaCompatibleOpenAiTest {

    private HttpServer servidor;

    private String iniciar(int demoraMs, String respuesta, AtomicReference<String> autorizacion, AtomicReference<String> cuerpo)
            throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/v1/chat/completions", intercambio -> {
            autorizacion.set(intercambio.getRequestHeaders().getFirst("Authorization"));
            cuerpo.set(new String(intercambio.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            try { Thread.sleep(demoraMs); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            byte[] bytes = respuesta.getBytes(StandardCharsets.UTF_8);
            intercambio.getResponseHeaders().add("Content-Type", "application/json");
            intercambio.sendResponseHeaders(200, bytes.length);
            intercambio.getResponseBody().write(bytes);
            intercambio.close();
        });
        servidor.start();
        return "http://127.0.0.1:" + servidor.getAddress().getPort() + "/v1";
    }

    @AfterEach
    void detener() {
        if (servidor != null) servidor.stop(0);
    }

    @Test
    @DisplayName("Envía la clave solo en la cabecera, pide JSON y devuelve el contenido del modelo")
    void llamadaCorrecta() throws IOException {
        AtomicReference<String> autorizacion = new AtomicReference<>();
        AtomicReference<String> cuerpo = new AtomicReference<>();
        String url = iniciar(0, "{\"choices\":[{\"message\":{\"content\":\"{\\\"intencion\\\":\\\"saludo\\\"}\"}}]}", autorizacion, cuerpo);

        ClienteIaCompatibleOpenAi cliente = new ClienteIaCompatibleOpenAi(RestClient.builder(), url, "modelo-prueba", "clave-falsa", 2000);

        assertThat(cliente.configurado()).isTrue();
        assertThat(cliente.completarJson("instrucciones", "hola")).isEqualTo("{\"intencion\":\"saludo\"}");
        assertThat(autorizacion.get()).isEqualTo("Bearer clave-falsa");
        assertThat(cuerpo.get()).contains("\"model\":\"modelo-prueba\"", "\"json_object\"", "hola")
                .doesNotContain("clave-falsa")
                // Solo los modelos gpt-oss aceptan reasoning_effort
                .doesNotContain("reasoning_effort");
    }

    @Test
    @DisplayName("Con gpt-oss pide razonamiento bajo y deja margen de tokens para el JSON")
    void gptOssConRazonamientoBajo() throws IOException {
        AtomicReference<String> autorizacion = new AtomicReference<>();
        AtomicReference<String> cuerpo = new AtomicReference<>();
        String url = iniciar(0, "{\"choices\":[{\"message\":{\"content\":\"{\\\"intencion\\\":\\\"saludo\\\"}\"}}]}", autorizacion, cuerpo);

        ClienteIaCompatibleOpenAi cliente = new ClienteIaCompatibleOpenAi(RestClient.builder(), url, "openai/gpt-oss-20b", "clave-falsa", 2000);

        assertThat(cliente.completarJson("instrucciones", "hola")).isEqualTo("{\"intencion\":\"saludo\"}");
        assertThat(cuerpo.get()).contains("\"reasoning_effort\":\"low\"", "\"max_tokens\":600");
    }

    @Test
    @DisplayName("Sin clave queda como no configurado")
    void sinClave() {
        assertThat(new ClienteIaCompatibleOpenAi(RestClient.builder(), "http://127.0.0.1:9", "m", "  ", 1000).configurado()).isFalse();
    }

    @Test
    @DisplayName("Corta la espera cuando el servicio tarda más que el límite")
    void tiempoAgotado() throws IOException {
        String url = iniciar(1500, "{}", new AtomicReference<>(), new AtomicReference<>());
        ClienteIaCompatibleOpenAi cliente = new ClienteIaCompatibleOpenAi(RestClient.builder(), url, "m", "clave-falsa", 300);
        assertThatThrownBy(() -> cliente.completarJson("i", "m")).isInstanceOf(ResourceAccessException.class);
    }
}
