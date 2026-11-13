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

// Un servidor local imita a ElevenLabs: no se llama al servicio real ni se usa una clave verdadera
@DisplayName("Fase 6 - Cliente de ElevenLabs")
class ClienteVozElevenLabsTest {

    private static final byte[] MP3 = {73, 68, 51, 4};
    private HttpServer servidor;

    private String iniciar(int demoraMs, AtomicReference<String> ruta, AtomicReference<String> clave, AtomicReference<String> cuerpo)
            throws IOException {
        servidor = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        servidor.createContext("/v1/text-to-speech/", intercambio -> {
            ruta.set(intercambio.getRequestURI().toString());
            clave.set(intercambio.getRequestHeaders().getFirst("xi-api-key"));
            cuerpo.set(new String(intercambio.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            try { Thread.sleep(demoraMs); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            intercambio.getResponseHeaders().add("Content-Type", "audio/mpeg");
            intercambio.sendResponseHeaders(200, MP3.length);
            intercambio.getResponseBody().write(MP3);
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
    @DisplayName("Usa la voz configurada, envía la clave solo en la cabecera y devuelve el MP3")
    void llamadaCorrecta() throws IOException {
        AtomicReference<String> ruta = new AtomicReference<>();
        AtomicReference<String> clave = new AtomicReference<>();
        AtomicReference<String> cuerpo = new AtomicReference<>();
        String url = iniciar(0, ruta, clave, cuerpo);

        ClienteVozElevenLabs voz = new ClienteVozElevenLabs(RestClient.builder(), url, "clave-falsa", "voz123", "eleven_flash_v2_5", 2000);

        assertThat(voz.configurado()).isTrue();
        assertThat(voz.sintetizar("Hola, soy FINDI")).isEqualTo(MP3);
        assertThat(ruta.get()).isEqualTo("/v1/text-to-speech/voz123?output_format=mp3_44100_64");
        assertThat(clave.get()).isEqualTo("clave-falsa");
        assertThat(cuerpo.get()).contains("Hola, soy FINDI", "eleven_flash_v2_5").doesNotContain("clave-falsa");
    }

    @Test
    @DisplayName("Sin clave o sin voz queda como no configurado")
    void sinConfigurar() {
        assertThat(new ClienteVozElevenLabs(RestClient.builder(), "http://127.0.0.1:9", "", "voz", "m", 1000).configurado()).isFalse();
        assertThat(new ClienteVozElevenLabs(RestClient.builder(), "http://127.0.0.1:9", "clave", " ", "m", 1000).configurado()).isFalse();
    }

    @Test
    @DisplayName("Corta la espera cuando ElevenLabs tarda más que el límite")
    void tiempoAgotado() throws IOException {
        String url = iniciar(1500, new AtomicReference<>(), new AtomicReference<>(), new AtomicReference<>());
        ClienteVozElevenLabs voz = new ClienteVozElevenLabs(RestClient.builder(), url, "clave-falsa", "voz123", "m", 300);
        assertThatThrownBy(() -> voz.sintetizar("hola")).isInstanceOf(ResourceAccessException.class);
    }
}
