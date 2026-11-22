package com.proyectointegrador.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.SignatureAlgorithm;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

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
import java.util.Date;
import java.util.Map;

/**
 * H57 y H72: cuenta de servicio de Firebase, leída solo de {@code FIREBASE_CREDENCIALES_JSON}.
 * Firma los JWT con la clave privada (token de acceso de Google y token personalizado de Auth)
 * sin librerías de Firebase. El contenido de la clave nunca se registra en el log.
 */
@Component
public class CuentaServicioFirebase {

    private static final Logger log = LoggerFactory.getLogger(CuentaServicioFirebase.class);
    /** Cubre FCM (push) y Identity Toolkit (revocar sesiones de Auth). */
    private static final String ALCANCE = "https://www.googleapis.com/auth/cloud-platform";
    /** Audiencia fija que Firebase exige en los tokens personalizados. */
    static final String AUDIENCIA_TOKEN_PERSONALIZADO =
            "https://identitytoolkit.googleapis.com/google.identity.identitytoolkit.v1.IdentityToolkit";

    private final ObjectMapper json;
    private final HttpClient http;
    private final String proyectoId;
    private final String correo;
    private final PrivateKey clave;
    private final String urlToken;

    private String tokenAcceso;
    private Instant tokenVence = Instant.EPOCH;

    public CuentaServicioFirebase(ObjectMapper json,
                                  @Value("${app.push.firebase-credenciales-json:}") String credenciales,
                                  @Value("${app.push.firebase-proyecto-id:}") String proyectoId) {
        this.json = json;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
        String proyecto = proyectoId == null ? "" : proyectoId.trim();
        String c = null, uri = null;
        PrivateKey k = null;
        if (credenciales != null && !credenciales.isBlank()) {
            try {
                JsonNode n = json.readTree(credenciales);
                c = n.path("client_email").asText(null);
                uri = n.path("token_uri").asText("https://oauth2.googleapis.com/token");
                k = leerClave(n.path("private_key").asText(""));
                if (proyecto.isEmpty()) proyecto = n.path("project_id").asText("");
            } catch (Exception ex) {
                log.error("FIREBASE_CREDENCIALES_JSON no es una cuenta de servicio válida; Firebase queda apagado.");
                c = null;
                k = null;
            }
        }
        this.proyectoId = proyecto;
        this.correo = c;
        this.clave = k;
        this.urlToken = uri;
    }

    public boolean disponible() {
        return clave != null && correo != null && !proyectoId.isEmpty();
    }

    public String proyectoId() {
        return proyectoId;
    }

    /**
     * H72: token personalizado para {@code signInWithCustomToken}. Firebase lo acepta como máximo
     * una hora. {@code claims} no debe llevar datos personales.
     */
    public String firmarTokenPersonalizado(String uid, Map<String, Object> claims, Instant ahora, Instant vence) {
        if (!disponible()) throw new IllegalStateException("Firebase no está configurado");
        return Jwts.builder()
                .setIssuer(correo)
                .setSubject(correo)
                .setAudience(AUDIENCIA_TOKEN_PERSONALIZADO)
                .setIssuedAt(Date.from(ahora))
                .setExpiration(Date.from(vence))
                .claim("uid", uid)
                .claim("claims", claims)
                .signWith(clave, SignatureAlgorithm.RS256)
                .compact();
    }

    /** Token de acceso de Google, reutilizado hasta un minuto antes de vencer. */
    public synchronized String tokenDeAcceso() throws Exception {
        if (!disponible()) throw new IllegalStateException("Firebase no está configurado");
        Instant ahora = Instant.now();
        if (tokenAcceso != null && ahora.isBefore(tokenVence.minusSeconds(60))) return tokenAcceso;
        String asercion = Jwts.builder()
                .setIssuer(correo)
                .setAudience(urlToken)
                .claim("scope", ALCANCE)
                .setIssuedAt(Date.from(ahora))
                .setExpiration(Date.from(ahora.plusSeconds(3600)))
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

    public HttpClient http() {
        return http;
    }

    public ObjectMapper json() {
        return json;
    }

    private static PrivateKey leerClave(String pem) throws Exception {
        String base64 = pem.replace("-----BEGIN PRIVATE KEY-----", "")
                .replace("-----END PRIVATE KEY-----", "")
                .replaceAll("\\s", "");
        byte[] der = Base64.getDecoder().decode(base64);
        return KeyFactory.getInstance("RSA").generatePrivate(new PKCS8EncodedKeySpec(der));
    }
}
