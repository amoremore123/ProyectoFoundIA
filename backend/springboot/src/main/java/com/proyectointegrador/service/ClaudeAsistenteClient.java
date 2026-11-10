package com.proyectointegrador.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.proyectointegrador.entity.Categoria;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * H45: cliente opcional del proveedor de IA (Claude/Anthropic).
 *
 * Solo se usa si app.asistente.claude.enabled=true y existe la clave en una
 * variable de entorno. Nunca se envía el catálogo de datos personales: el
 * mensaje se redacta y solo viajan el texto y los nombres de las categorías.
 * Si el proveedor falla o tarda, el servicio usa el motor local.
 */
@Component
public class ClaudeAsistenteClient {

    private static final Logger log = LoggerFactory.getLogger(ClaudeAsistenteClient.class);

    private static final Pattern CORREO = Pattern.compile("[\\w.+-]+@[\\w.-]+\\.[a-zA-Z]{2,}");
    private static final Pattern TELEFONO = Pattern.compile("\\b\\d{7,15}\\b");

    private static final String SISTEMA = """
            Eres el intérprete de búsquedas de FoundIA, una plataforma de objetos perdidos.
            Devuelve SOLO un objeto JSON válido, sin markdown ni explicaciones, con esta forma:
            {"intencion":"buscar|publicar|ayuda|saludo|gracias|vacio",
             "tipoBuscado":"PERDIDO|ENCONTRADO|",
             "categoriaId":numero o null,
             "ubicacion":"texto o vacio",
             "palabras":["..."],
             "colores":["..."]}
            Reglas: usa solo las categorías de la lista; no inventes ids; no incluyas
            nombres, correos, teléfonos ni otros datos personales; devuelve como máximo
            12 palabras y 6 colores en singular y sin tildes.
            """;

    private final ObjectMapper objectMapper;
    private final boolean habilitado;
    private final String apiKey;
    private final String modelo;
    private final String url;
    private final long timeoutMs;
    private final HttpClient httpClient;

    public ClaudeAsistenteClient(
            ObjectMapper objectMapper,
            @Value("${app.asistente.claude.enabled:false}") boolean habilitado,
            @Value("${app.asistente.claude.api-key:}") String apiKey,
            @Value("${app.asistente.claude.model:claude-sonnet-4-5}") String modelo,
            @Value("${app.asistente.claude.url:https://api.anthropic.com/v1/messages}") String url,
            @Value("${app.asistente.claude.timeout-ms:8000}") long timeoutMs) {
        this.objectMapper = objectMapper;
        this.habilitado = habilitado;
        this.apiKey = apiKey == null ? "" : apiKey.trim();
        this.modelo = modelo;
        this.url = url;
        this.timeoutMs = Math.max(1000, timeoutMs);
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(this.timeoutMs))
                .build();
    }

    public Optional<IaInterpretacion> interpretar(String mensaje, List<Categoria> categorias) {
        if (!habilitado || apiKey.isBlank()) {
            return Optional.empty();
        }
        try {
            ObjectNode cuerpo = objectMapper.createObjectNode();
            cuerpo.put("model", modelo);
            cuerpo.put("max_tokens", 300);
            cuerpo.put("temperature", 0);
            cuerpo.put("system", SISTEMA);
            ArrayNode mensajes = cuerpo.putArray("messages");
            ObjectNode usuario = mensajes.addObject();
            usuario.put("role", "user");
            usuario.put("content", "Mensaje: " + redactar(mensaje) + "\nCategorías: " + categorias(categorias));

            HttpRequest solicitud = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofMillis(timeoutMs))
                    .header("content-type", "application/json")
                    .header("x-api-key", apiKey)
                    .header("anthropic-version", "2023-06-01")
                    .POST(HttpRequest.BodyPublishers.ofString(objectMapper.writeValueAsString(cuerpo)))
                    .build();

            HttpResponse<String> respuesta = httpClient.send(
                    solicitud, HttpResponse.BodyHandlers.ofString());
            if (respuesta.statusCode() != 200) {
                log.warn("El proveedor de IA respondió {}; se usa el motor local", respuesta.statusCode());
                return Optional.empty();
            }

            JsonNode raiz = objectMapper.readTree(respuesta.body());
            String texto = raiz.path("content").path(0).path("text").asText("");
            int inicio = texto.indexOf('{');
            int fin = texto.lastIndexOf('}');
            if (inicio < 0 || fin <= inicio) {
                return Optional.empty();
            }
            return Optional.of(objectMapper.readValue(
                    texto.substring(inicio, fin + 1), IaInterpretacion.class));
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        } catch (Exception ex) {
            // No se registra el mensaje ni la clave: solo el tipo de fallo.
            log.warn("No se pudo usar el proveedor de IA ({}); se usa el motor local",
                    ex.getClass().getSimpleName());
            return Optional.empty();
        }
    }

    private String redactar(String mensaje) {
        String sinCorreo = CORREO.matcher(mensaje == null ? "" : mensaje).replaceAll("[dato personal]");
        return TELEFONO.matcher(sinCorreo).replaceAll("[dato personal]");
    }

    private String categorias(List<Categoria> categorias) {
        StringBuilder texto = new StringBuilder();
        int incluidas = 0;
        for (Categoria categoria : categorias) {
            if (!Boolean.TRUE.equals(categoria.getEstado()) || incluidas >= 30) {
                continue;
            }
            String descripcion = categoria.getDescripcion() == null ? "" : categoria.getDescripcion();
            if (descripcion.length() > 60) {
                descripcion = descripcion.substring(0, 60);
            }
            if (incluidas > 0) {
                texto.append("; ");
            }
            texto.append(categoria.getId()).append("=").append(categoria.getNombre());
            if (!descripcion.isBlank()) {
                texto.append(" (").append(descripcion).append(")");
            }
            incluidas++;
        }
        return texto.toString();
    }
}
