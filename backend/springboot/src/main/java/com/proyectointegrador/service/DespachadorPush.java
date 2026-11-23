package com.proyectointegrador.service;

import com.proyectointegrador.dto.PreferenciasRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * H57: cada pocos segundos envía por push los avisos nuevos de {@code notificaciones}, sean de
 * Spring o de Django. Cada aviso se marca ({@code push_enviado_en}) antes de enviarlo, con una
 * actualización condicional: aunque corran dos instancias, se envía una sola vez.
 */
@Service
public class DespachadorPush {

    private static final Logger log = LoggerFactory.getLogger(DespachadorPush.class);
    private static final int LOTE = 100;

    private final JdbcTemplate jdbc;
    private final EnviadorPush enviador;
    private final PreferenciaNotificacionService preferencias;
    private final long ventanaMinutos;
    private final AtomicBoolean avisoApagado = new AtomicBoolean();

    public DespachadorPush(JdbcTemplate jdbc, EnviadorPush enviador, PreferenciaNotificacionService preferencias,
                           @Value("${app.push.ventana-minutos:30}") long ventanaMinutos) {
        this.jdbc = jdbc;
        this.enviador = enviador;
        this.preferencias = preferencias;
        this.ventanaMinutos = ventanaMinutos;
    }

    private record Aviso(long id, long usuarioId, String tipo, String titulo, String mensaje, String enlace) {}

    @Scheduled(fixedDelayString = "${app.push.intervalo-ms:15000}", initialDelayString = "${app.push.retraso-inicial-ms:20000}")
    public void programado() {
        try {
            despachar();
        } catch (RuntimeException ex) {
            log.warn("El despachador de push falló en esta vuelta: {}", ex.getClass().getSimpleName());
        }
    }

    /** @return cuántos push se enviaron con éxito en esta vuelta */
    public int despachar() {
        if (!enviador.disponible()) {
            if (avisoApagado.compareAndSet(false, true)) {
                log.info("Push apagado: falta FIREBASE_CREDENCIALES_JSON. Los avisos se guardan igual.");
            }
            return 0;
        }
        LocalDateTime ahora = LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS);
        // Solo avisos recientes: al encender el push no llega el historial viejo
        List<Aviso> pendientes = jdbc.query("""
                SELECT id, usuario_id, tipo, titulo, mensaje, enlace FROM notificaciones
                 WHERE push_enviado_en IS NULL AND fecha_creacion >= ?
                 ORDER BY id LIMIT ?""",
                (rs, i) -> new Aviso(rs.getLong("id"), rs.getLong("usuario_id"), rs.getString("tipo"),
                        rs.getString("titulo"), rs.getString("mensaje"), rs.getString("enlace")),
                Timestamp.valueOf(ahora.minusMinutes(ventanaMinutos)), LOTE);
        int enviados = 0;
        for (Aviso a : pendientes) {
            // Se reclama el aviso: si otra instancia ya lo tomó, no se envía de nuevo
            if (jdbc.update("UPDATE notificaciones SET push_enviado_en = ? WHERE id = ? AND push_enviado_en IS NULL",
                    Timestamp.valueOf(ahora), a.id()) != 1) {
                continue;
            }
            if (!permitido(a)) continue;
            List<String> tokens = jdbc.queryForList("SELECT token FROM dispositivos WHERE usuario_id = ?",
                    String.class, a.usuarioId());
            var mensaje = new EnviadorPush.Mensaje(a.titulo(), a.mensaje(), a.id(), a.tipo(), a.enlace());
            for (String token : tokens) {
                switch (enviador.enviar(token, mensaje)) {
                    case ENVIADO -> enviados++;
                    case TOKEN_INVALIDO -> jdbc.update("DELETE FROM dispositivos WHERE token = ?", token);
                    case ERROR -> { }
                }
            }
        }
        return enviados;
    }

    /** Preferencias de H24 por tipo de aviso y canal de ese tipo (H68); los tipos desconocidos no envían push. */
    private boolean permitido(Aviso a) {
        return PreferenciaNotificacionService.permitePush(preferencias.obtener(a.usuarioId()), a.tipo());
    }
}
