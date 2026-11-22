package com.proyectointegrador.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyFactory;
import java.security.PrivateKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * H57: cliente de Firebase Cloud Messaging (API HTTP v1) sin librerías extra: firma con la
 * cuenta de servicio un JWT para obtener el token de acceso de Google y envía cada mensaje.
 * La cuenta de servicio llega solo por variable de entorno ({@code FIREBASE_CREDENCIALES_JSON}).
 */
@Service
public class ClienteFcm implements EnviadorPush {

    private static final Logger log = LoggerFactory.getLogger(ClienteFcm.class);
    private static final String ALCANCE = "https://www.googleapis.com/auth/firebase.messaging";

    private final ObjectMapper json;
    private final HttpClient http;
    private final String proyectoId;
    private final String correoCuenta;
    private final PrivateKey clave;
    private final String urlToken;
    private final String urlFcm;

    private String tokenAcceso;
    private Instant tokenVence = Instant.EPOCH;

    public ClienteFcm(ObjectMapper json,
                      @Value("${app.push.firebase-credenciales-json:}") String credenciales,
                      @Value("${app.push.firebase-proyecto-id:}") String proyectoId,
                      @Value("${app.push.fcm-url:https://fcm.googleapis.com}") String urlFcm) {
        this.json = json;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        this.urlFcm = urlFcm.replaceFirst("/+$", "");
        String proyecto = proyectoId == null ? "" : proyectoId.trim();
        String correo = null, uri = null;
        PrivateKey k = null;
        if (credenciales != null && !credenciales.isBlank()) {
            try {
                JsonNode c = json.readTree(credenciales);
                correo = c.path("client_email").asText(null);
                uri = c.path("token_uri").asText("https://oauth2.googleapis.com/token");
                k = leerClave(c.path("private_key").asText(""));
                if (proyecto.isEmpty()) proyecto = c.path("project_id").asText("");
            } catch (Exception ex) {
                // Nunca se registra el contenido: solo que la configuración no es válida
                log.error("FIREBASE_CREDENCIALES_JSON no es una cuenta de servicio válida; el push queda apagado.");
                correo = null;
                k = null;
            }
        }
        this.proyectoId = proyecto;
        this.correoCuenta = correo;
        this.clave = k;
        this.urlToken = uri;
    }

    @Override
    public boolean disponible() {
        return clave != null && correoCuenta != null && !proyectoId.isEmpty();
    }

    @Override
    public Resultado enviar(String tokenDispositivo, Mensaje m) {
        if (!disponible()) return Resultado.ERROR;
        try {
            Map<String, String> datos = new LinkedHashMap<>();
            datos.put("notificacionId", String.valueOf(m.notificacionId()));
            datos.put("tipo", m.tipo() == null ? "" : m.tipo());
            if (m.enlace() != null) datos.put("enlace", m.enlace());
            String canal = "MENSAJE_CHAT".equals(m.tipo()) ? "mensajes" : "avisos";
            Map<String, Object> cuerpo = Map.of("message", Map.of(
                    "token", tokenDispositivo,
                    "notification", Map.of("title", m.titulo(), "body", m.cuerpo()),
                    "data", datos,
                    "android", Map.of("notification", Map.of("channel_id", canal))));
            HttpRequest pedido = HttpRequest.newBuilder(URI.create(urlFcm + "/v1/projects/" + proyectoId + "/messages:send"))
                    .timeout(Duration.ofSeconds(15))
                    .header("Authorization", "Bearer " + tokenDeAcceso())
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .POST(HttpRequest.BodyPublishers.ofString(json.writeValueAsString(cuerpo)))
                    .build();
            HttpResponse<String> r = http.send(pedido, HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() == 200) return Resultado.ENVIADO;
            if (tokenRechazado(r)) return Resultado.TOKEN_INVALIDO;
            log.warn("FCM respondió {} al enviar el aviso {}", r.statusCode(), m.notificacionId());
            return Resultado.ERROR;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return Resultado.ERROR;
        } catch (Exception ex) {
            log.warn("No se pudo enviar el push del aviso {}: {}", m.notificacionId(), ex.getClass().getSimpleName());
            return Resultado.ERROR;
        }
    }

    /** UNREGISTERED (404) o un token con formato inválido (400 INVALID_ARGUMENT): el teléfono ya no existe. */
    private boolean tokenRechazado(HttpResponse<String> r) {
        String cuerpo = r.body() == null ? "" : r.body();
        return (r.statusCode() == 404 && cuerpo.contains("UNREGISTERED"))
                || (r.statusCode() == 400 && cuerpo.contains("INVALID_ARGUMENT") && cuerpo.contains("token"));
    }

    private synchronized String tokenDeAcceso() throws Exception {
        Instant ahora = Instant.now();
        if (tokenAcceso != null && ahora.isBefore(tokenVence.minusSeconds(60))) return tokenAcceso;
        String asercion = Jwts.builder()
                .setIssuer(correoCuenta)
                .setAudience(urlToken)
                .claim("scope", ALCANCE)
                .setIssuedAt(java.util.Date.from(ahora))
                .setExpiration(java.util.Date.from(ahora.plusSeconds(3600)))
                .signWith(clave, SignatureAlgorithm.RS256)
                .compact();
        String formulario = "grant_type=" + URLEncoder.encode("urn:ietf:params:oauth:grant-type:jwt-bearer", StandardCharsets.UTF_8)
                + "&assertion=" + URLEncoder.encode(asercion, StandardCharsets.UTF_8);
        HttpResponse<String> r = http.send(HttpRequest.newBuilder(URI.create(urlToken))
                        .timeout(Duration.ofSeconds(15))
                        .header("Content-Type", "application/x-www-form-urlencoded")
                        .POST(HttpRequest.BodyPublishers.ofString(formulario)).build(),
                HttpResponse.BodyHandlers.ofString());
        if (r.statusCode() != 200) {
            throw new IllegalStateException("Google no entregó el token de acceso (" + r.statusCode() + ")");
        }
        JsonNode t = json.readTree(r.body());
        tokenAcceso = t.path("access_token").asText();
        tokenVence = ahora.plusSeconds(t.path("expires_in").asLong(3600));
        return tokenAcceso;
    }

    private static PrivateKey leerClave(String pem) throws Exception {
        String base64 = pem.replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        byte[] der = Base64.getDecoder().decode(base64);
        return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
    }
}
