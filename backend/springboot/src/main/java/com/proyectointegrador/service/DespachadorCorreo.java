package com.proyectointegrador.service;

import com.proyectointegrador.dto.PreferenciasRequest;
import com.proyectointegrador.exception.EnvioCorreoException;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * H68: cada minuto envía por correo los avisos nuevos de {@code notificaciones} (de Spring o de
 * Django) a quien eligió ese canal. Igual que el push de H57, cada aviso se reclama con una
 * actualización condicional ({@code correo_enviado_en}) antes de enviarlo: se envía una sola vez.
 */
@Service
public class DespachadorCorreo {

    private static final Logger log = LoggerFactory.getLogger(DespachadorCorreo.class);
    private static final int LOTE = 50;
    private static final Pattern CONVERSACION = Pattern.compile("^/conversaciones/(\\d+)$");

    public static final String ENVIADO = "ENVIADO";
    public static final String OMITIDO = "OMITIDO";
    public static final String ERROR = "ERROR";

    private final JdbcTemplate jdbc;
    private final EmailService email;
    private final PreferenciaNotificacionService preferencias;
    private final String webUrl;
    private final long ventanaMinutos;
    private final long chatMinutos;

    public DespachadorCorreo(JdbcTemplate jdbc, EmailService email, PreferenciaNotificacionService preferencias,
                             @Value("${app.web.url:http://localhost:5173}") String webUrl,
                             @Value("${app.correo-avisos.ventana-minutos:30}") long ventanaMinutos,
                             @Value("${app.correo-avisos.chat-minutos:30}") long chatMinutos) {
        this.jdbc = jdbc;
        this.email = email;
        this.preferencias = preferencias;
        this.webUrl = webUrl.endsWith("/") ? webUrl.substring(0, webUrl.length() - 1) : webUrl;
        this.ventanaMinutos = ventanaMinutos;
        this.chatMinutos = chatMinutos;
    }

    private record Aviso(long id, long usuarioId, String tipo, String titulo, String mensaje, String enlace,
                         String correo, String nombre, String estado, boolean verificado) {}

    @Scheduled(fixedDelayString = "${app.correo-avisos.intervalo-ms:60000}",
               initialDelayString = "${app.correo-avisos.retraso-inicial-ms:30000}")
    public void programado() {
        try {
            despachar();
        } catch (RuntimeException ex) {
            log.warn("El despachador de correos de aviso falló en esta vuelta: {}", ex.getClass().getSimpleName());
        }
    }

    /** @return cuántos correos se enviaron con éxito en esta vuelta */
    public int despachar() {
        LocalDateTime ahora = LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS);
        // Solo avisos recientes: al activar el correo no llega el historial viejo
        List<Aviso> pendientes = jdbc.query("""
                SELECT n.id, n.usuario_id, n.tipo, n.titulo, n.mensaje, n.enlace,
                       u.correo, u.nombre, u.estado, u.verificado
                  FROM notificaciones n JOIN usuarios u ON u.id = n.usuario_id
                 WHERE n.correo_enviado_en IS NULL AND n.fecha_creacion >= ?
                 ORDER BY n.id LIMIT ?""",
                (rs, i) -> new Aviso(rs.getLong("id"), rs.getLong("usuario_id"), rs.getString("tipo"),
                        rs.getString("titulo"), rs.getString("mensaje"), rs.getString("enlace"),
                        rs.getString("correo"), rs.getString("nombre"), rs.getString("estado"),
                        rs.getBoolean("verificado")),
                Timestamp.valueOf(ahora.minusMinutes(ventanaMinutos)), LOTE);
        int enviados = 0;
        for (Aviso a : pendientes) {
            // Se reclama el aviso: si otra instancia ya lo tomó, no se envía de nuevo
            if (jdbc.update("UPDATE notificaciones SET correo_enviado_en = ? WHERE id = ? AND correo_enviado_en IS NULL",
                    Timestamp.valueOf(ahora), a.id()) != 1) {
                continue;
            }
            String resultado = enviar(a, ahora);
            jdbc.update("UPDATE notificaciones SET correo_resultado = ? WHERE id = ?", resultado, a.id());
            if (ENVIADO.equals(resultado)) enviados++;
        }
        return enviados;
    }

    private String enviar(Aviso a, LocalDateTime ahora) {
        if (!"ACTIVO".equals(a.estado()) || !a.verificado() || a.correo() == null) return OMITIDO;
        PreferenciasRequest p = preferencias.obtener(a.usuarioId());
        if (!Boolean.TRUE.equals(p.correo()) || !PreferenciaNotificacionService.tipoActivo(p, a.tipo())) return OMITIDO;
        if ("MENSAJE_CHAT".equals(a.tipo()) && chatYaAvisado(a, ahora)) return OMITIDO;
        try {
            email.enviarAviso(a.correo(), a.nombre(), a.titulo(), a.mensaje(), enlaceWeb(a.enlace()));
            return ENVIADO;
        } catch (EnvioCorreoException | org.springframework.mail.MailException ex) {
            return ERROR;
        }
    }

    /** Un correo por conversación cada {@code chatMinutos}: los siguientes quedan en la bandeja y el push. */
    private boolean chatYaAvisado(Aviso a, LocalDateTime ahora) {
        Integer previos = jdbc.queryForObject("""
                SELECT COUNT(*) FROM notificaciones
                 WHERE usuario_id = ? AND tipo = 'MENSAJE_CHAT' AND enlace = ? AND id <> ?
                   AND correo_resultado = 'ENVIADO' AND correo_enviado_en >= ?""",
                Integer.class, a.usuarioId(), a.enlace(), a.id(), Timestamp.valueOf(ahora.minusMinutes(chatMinutos)));
        return previos != null && previos > 0;
    }

    /** La web abre la conversación en /mensajes/{id}; lo demás, en Notificaciones. */
    String enlaceWeb(String enlace) {
        if (enlace != null) {
            Matcher m = CONVERSACION.matcher(enlace);
            if (m.matches()) return webUrl + "/mensajes/" + m.group(1);
        }
        return webUrl + "/notificaciones";
    }
}
