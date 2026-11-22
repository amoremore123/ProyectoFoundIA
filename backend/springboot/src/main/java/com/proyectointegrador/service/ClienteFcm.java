package com.proyectointegrador.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * H57: cliente de Firebase Cloud Messaging (API HTTP v1). La autenticación con Google la hace
 * {@link CuentaServicioFirebase}, compartida con Firebase Auth (H72).
 */
@Service
public class ClienteFcm implements EnviadorPush {

    private static final Logger log = LoggerFactory.getLogger(ClienteFcm.class);

    private final CuentaServicioFirebase cuenta;
    private final String urlFcm;

    public ClienteFcm(CuentaServicioFirebase cuenta,
                      @Value("${app.push.fcm-url:https://fcm.googleapis.com}") String urlFcm) {
        this.cuenta = cuenta;
        this.urlFcm = urlFcm.replaceFirst("/+$", "");
    }

    @Override
    public boolean disponible() {
        return cuenta.disponible();
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
            HttpRequest pedido = HttpRequest.newBuilder(
                            URI.create(urlFcm + "/v1/projects/" + cuenta.proyectoId() + "/messages:send"))
                    .timeout(Duration.ofSeconds(15))
                    .header("Authorization", "Bearer " + cuenta.tokenDeAcceso())
                    .header("Content-Type", "application/json; charset=UTF-8")
                    .POST(HttpRequest.BodyPublishers.ofString(cuenta.json().writeValueAsString(cuerpo)))
                    .build();
            HttpResponse<String> r = cuenta.http().send(pedido, HttpResponse.BodyHandlers.ofString());
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
}
