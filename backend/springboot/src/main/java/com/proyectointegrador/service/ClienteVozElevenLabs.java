package com.proyectointegrador.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.Map;

/**
 * Fase 6 - Texto a voz con ElevenLabs. La clave viaja solo en la cabecera xi-api-key
 * desde el servidor (variable VOZ_API_KEY); nunca llega a la web ni se registra.
 */
@Component
public class ClienteVozElevenLabs implements ClienteVoz {

    private final RestClient http;
    private final String apiKey;
    private final String vozId;
    private final String modelo;

    public ClienteVozElevenLabs(RestClient.Builder builder,
                                @Value("${app.voz.url:https://api.elevenlabs.io/v1}") String url,
                                @Value("${app.voz.api-key:}") String apiKey,
                                @Value("${app.voz.voz-id:}") String vozId,
                                @Value("${app.voz.modelo:eleven_flash_v2_5}") String modelo,
                                @Value("${app.voz.timeout-ms:10000}") int timeoutMs) {
        HttpClient clienteHttp = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(timeoutMs)).build();
        JdkClientHttpRequestFactory fabrica = new JdkClientHttpRequestFactory(clienteHttp);
        fabrica.setReadTimeout(Duration.ofMillis(timeoutMs));
        this.http = builder.baseUrl(url).requestFactory(new BufferingClientHttpRequestFactory(fabrica)).build();
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.vozId = vozId == null ? "" : vozId.trim();
        this.modelo = modelo;
    }

    @Override
    public boolean configurado() {
        return !apiKey.isEmpty() && !vozId.isEmpty();
    }

    @Override
    public byte[] sintetizar(String texto) {
        return http.post()
                .uri(uri -> uri.path("/text-to-speech/{voz}").queryParam("output_format", "mp3_44100_64").build(vozId))
                .header("xi-api-key", apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .accept(MediaType.valueOf("audio/mpeg"))
                .body(Map.of("text", texto, "model_id", modelo))
                .retrieve()
                .body(byte[].class);
    }
}
