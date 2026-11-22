package com.proyectointegrador.service;

import com.proyectointegrador.entity.Usuario;
import com.proyectointegrador.exception.FirebaseNoDisponibleException;
import com.proyectointegrador.exception.ResourceNotFoundException;
import com.proyectointegrador.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Map;
import java.util.concurrent.CompletableFuture;

/**
 * H72: Firebase Auth encima de la sesión de FoundIA. Emite tokens personalizados con un UID
 * estable ({@code foundia-<id>}) sin datos personales, y revoca las sesiones de Firebase de una
 * cuenta al cerrar en todos los dispositivos. Ninguna ruta de FoundIA acepta tokens de Firebase.
 */
@Service
public class FirebaseAuthService {

    private static final Logger log = LoggerFactory.getLogger(FirebaseAuthService.class);
    /** Firebase acepta un token personalizado como máximo una hora. */
    static final long VIGENCIA_SEGUNDOS = 3600;

    private final CuentaServicioFirebase cuenta;
    private final UsuarioRepository usuarios;
    private final String urlIdentityToolkit;

    public FirebaseAuthService(CuentaServicioFirebase cuenta, UsuarioRepository usuarios,
                               @Value("${app.firebase.identitytoolkit-url:https://identitytoolkit.googleapis.com}") String url) {
        this.cuenta = cuenta;
        this.usuarios = usuarios;
        this.urlIdentityToolkit = url.replaceFirst("/+$", "");
    }

    public record TokenFirebase(String token, String uid, Instant expiraEn) {}

    public static String uid(Long usuarioId) {
        return "foundia-" + usuarioId;
    }

    @Transactional(readOnly = true)
    public TokenFirebase emitir(Long usuarioId) {
        if (!cuenta.disponible()) throw new FirebaseNoDisponibleException();
        Usuario u = usuarios.findById(usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado"));
        Instant ahora = Instant.now().truncatedTo(ChronoUnit.SECONDS);
        Instant vence = ahora.plusSeconds(VIGENCIA_SEGUNDOS);
        // Solo el rol: nunca correo, nombre ni teléfono
        String token = cuenta.firmarTokenPersonalizado(uid(u.getId()), Map.of("rol", u.getRol().name()), ahora, vence);
        return new TokenFirebase(token, uid(u.getId()), vence);
    }

    /**
     * Revoca en Firebase las sesiones de la cuenta cuando termine la transacción actual (o ya,
     * si no hay una). Se hace en segundo plano: si Google no responde, cerrar sesión no falla.
     */
    public void revocarAlConfirmar(Long usuarioId) {
        if (!cuenta.disponible() || usuarioId == null) return;
        Runnable tarea = () -> CompletableFuture.runAsync(() -> revocar(usuarioId));
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCommit() {
                    tarea.run();
                }
            });
        } else {
            tarea.run();
        }
    }

    /** Equivale a {@code revokeRefreshTokens(uid)} del SDK de administración. */
    public boolean revocar(Long usuarioId) {
        if (!cuenta.disponible()) return false;
        try {
            String cuerpo = cuenta.json().writeValueAsString(Map.of(
                    "localId", uid(usuarioId),
                    "validSince", String.valueOf(Instant.now().getEpochSecond())));
            HttpResponse<String> r = cuenta.http().send(HttpRequest.newBuilder(URI.create(
                                    urlIdentityToolkit + "/v1/projects/" + cuenta.proyectoId() + "/accounts:update"))
                            .timeout(Duration.ofSeconds(15))
                            .header("Authorization", "Bearer " + cuenta.tokenDeAcceso())
                            .header("Content-Type", "application/json; charset=UTF-8")
                            .POST(HttpRequest.BodyPublishers.ofString(cuerpo)).build(),
                    HttpResponse.BodyHandlers.ofString());
            if (r.statusCode() == 200) return true;
            // Si la cuenta nunca entró a Firebase no hay nada que revocar
            if (r.body() != null && r.body().contains("USER_NOT_FOUND")) return true;
            log.warn("Firebase respondió {} al revocar las sesiones de la cuenta {}", r.statusCode(), usuarioId);
            return false;
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
            return false;
        } catch (Exception ex) {
            log.warn("No se pudieron revocar las sesiones de Firebase de la cuenta {}: {}", usuarioId,
                    ex.getClass().getSimpleName());
            return false;
        }
    }
}
