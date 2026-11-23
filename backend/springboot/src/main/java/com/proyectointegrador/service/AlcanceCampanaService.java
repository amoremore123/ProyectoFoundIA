package com.proyectointegrador.service;

import com.proyectointegrador.dto.VisitaCampanaRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * H67: cuenta visitas y registros por origen de campaña (H60). No guarda datos personales: ni la IP
 * (el límite por minuto vive solo en memoria) ni qué cuenta se registró.
 */
@Service
public class AlcanceCampanaService {

    private static final Logger log = LoggerFactory.getLogger(AlcanceCampanaService.class);
    public static final int VISITAS_POR_MINUTO = 30;
    private static final long VENTANA_MS = 60_000;
    private static final int MAX_CLAVES = 10_000;

    private final JdbcTemplate jdbc;
    private final LongSupplier reloj;
    private final Map<String, Deque<Long>> recientes = new ConcurrentHashMap<>();

    @org.springframework.beans.factory.annotation.Autowired
    public AlcanceCampanaService(JdbcTemplate jdbc) {
        this(jdbc, System::currentTimeMillis);
    }

    AlcanceCampanaService(JdbcTemplate jdbc, LongSupplier reloj) {
        this.jdbc = jdbc;
        this.reloj = reloj;
    }

    /** @return {@code false} si el equipo superó el límite por minuto (no se guarda nada) */
    public boolean dentroDelLimite(String equipo) {
        long ahora = reloj.getAsLong();
        if (recientes.size() > MAX_CLAVES) {
            recientes.entrySet().removeIf(e -> {
                synchronized (e.getValue()) {
                    Long ultima = e.getValue().peekLast();
                    return ultima == null || ahora - ultima >= VENTANA_MS;
                }
            });
        }
        Deque<Long> historial = recientes.computeIfAbsent(equipo, c -> new ArrayDeque<>());
        synchronized (historial) {
            while (!historial.isEmpty() && ahora - historial.peekFirst() >= VENTANA_MS) historial.pollFirst();
            if (historial.size() >= VISITAS_POR_MINUTO) return false;
            historial.addLast(ahora);
            return true;
        }
    }

    /** Suma la visita una sola vez por {@code visitaId}. Un origen que no existe se ignora. */
    public void registrarVisita(VisitaCampanaRequest visita) {
        Long campana = campanaDe(visita.origen());
        if (campana == null) return;
        try {
            jdbc.update("INSERT INTO alcance_campanas (campana_id, tipo, visita_id, fecha) VALUES (?, 'VISITA', ?, ?)",
                    campana, visita.visitaId(), ahora());
        } catch (DuplicateKeyException repetida) {
            // La misma visita (recarga o misma pestaña): ya se contó
        }
    }

    /**
     * Suma un registro si el origen es de una campaña. Corre dentro de la transacción del registro:
     * si el registro se revierte, el conteo también. Nunca hace fallar el registro.
     */
    public void registrarRegistro(String origen) {
        if (origen == null || !origen.matches(VisitaCampanaRequest.AlcanceFormato.ORIGEN)) return;
        try {
            Long campana = campanaDe(origen);
            if (campana == null) return;
            jdbc.update("INSERT INTO alcance_campanas (campana_id, tipo, visita_id, fecha) VALUES (?, 'REGISTRO', NULL, ?)",
                    campana, ahora());
        } catch (DataAccessException ex) {
            log.warn("No se pudo contar el registro de una campaña: {}", ex.getClass().getSimpleName());
        }
    }

    private Long campanaDe(String origen) {
        List<Long> ids = jdbc.queryForList("SELECT id FROM campanas WHERE origen = ?", Long.class, origen);
        return ids.isEmpty() ? null : ids.get(0);
    }

    private Timestamp ahora() {
        return Timestamp.valueOf(LocalDateTime.now(ZoneOffset.UTC));
    }
}
