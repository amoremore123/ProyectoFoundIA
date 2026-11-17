package com.proyectointegrador.service;

import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.MediaType;
import org.springframework.http.client.BufferingClientHttpRequestFactory;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.List;
import java.util.Map;

/**
 * Fase 5 - Cliente para servicios con el formato de OpenAI (/chat/completions): Groq u OpenAI.
 * La clave vive solo en el servidor (variable IA_API_KEY) y nunca se registra ni se devuelve.
 */
@Component
public class ClienteIaCompatibleOpenAi implements ClienteIa {

    private final RestClient http;
    private final String modelo;
    private final String modeloVision;
    private final String apiKey;

    public ClienteIaCompatibleOpenAi(RestClient.Builder builder, String url, String modelo, String apiKey, int timeoutMs) {
        this(builder, url, modelo, "qwen/qwen3.8-27b", apiKey, timeoutMs);
    }

    @Autowired
    public ClienteIaCompatibleOpenAi(RestClient.Builder builder,
                                     @Value("${app.ia.url:https://api.groq.com/openai/v1}") String url,
                                     @Value("${app.ia.modelo:openai/gpt-oss-20b}") String modelo,
                                     // Búsqueda por foto: modelo que acepta imágenes (los gpt-oss solo aceptan texto)
                                     @Value("${app.ia.modelo-vision:qwen/qwen3.8-27b}") String modeloVision,
                                     @Value("${app.ia.api-key:}") String apiKey,
                                     @Value("${app.ia.timeout-ms:8000}") int timeoutMs) {
        // Cliente HTTP moderno de Java: respeta el tiempo máximo y no reintenta el POST por su cuenta
        HttpClient clienteHttp = HttpClient.newBuilder().connectTimeout(Duration.ofMillis(timeoutMs)).build();
        JdkClientHttpRequestFactory fabrica = new JdkClientHttpRequestFactory(clienteHttp);
        fabrica.setReadTimeout(Duration.ofMillis(timeoutMs));
        // Con buffer se envía Content-Length: algunos servidores y proxies rechazan el cuerpo por partes
        this.http = builder.baseUrl(url).requestFactory(new BufferingClientHttpRequestFactory(fabrica)).build();
        this.modelo = modelo;
        this.modeloVision = modeloVision;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
    }

    @Override
    public boolean configurado() {
        return !apiKey.isEmpty();
    }

    @Override
    public String completarJson(String instrucciones, String mensaje) {
        Map<String, Object> cuerpo = new java.util.LinkedHashMap<>();
        cuerpo.put("model", modelo);
        cuerpo.put("temperature", 0);
        // Los modelos que razonan (gpt-oss) gastan tokens antes del JSON: con poco margen la
        // respuesta salía vacía y Groq la rechazaba (json_validate_failed).
        cuerpo.put("max_tokens", 600);
        if (modelo.startsWith("openai/gpt-oss")) cuerpo.put("reasoning_effort", "low");
        cuerpo.put("response_format", Map.of("type", "json_object"));
        cuerpo.put("messages", List.of(
                Map.of("role", "system", "content", instrucciones),
                Map.of("role", "user", "content", mensaje)));

        return enviar(cuerpo);
    }

    @Override
    public String completarJsonConImagen(String instrucciones, String texto, String imagenDataUrl) {
        Map<String, Object> cuerpo = new java.util.LinkedHashMap<>();
        cuerpo.put("model", modeloVision);
        cuerpo.put("temperature", 0);
        cuerpo.put("max_tokens", 400);
        cuerpo.put("response_format", Map.of("type", "json_object"));
        cuerpo.put("messages", List.of(
                Map.of("role", "system", "content", instrucciones),
                Map.of("role", "user", "content", List.of(
                        Map.of("type", "text", "text", texto),
                        Map.of("type", "image_url", "image_url", Map.of("url", imagenDataUrl))))));
        return enviar(cuerpo);
    }

    private String enviar(Map<String, Object> cuerpo) {
        JsonNode respuesta = http.post()
                .uri("/chat/completions")
                .header("Authorization", "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(cuerpo)
                .retrieve()
                .body(JsonNode.class);

        return respuesta == null ? null
                : respuesta.path("choices").path(0).path("message").path("content").asText(null);
    }
}
