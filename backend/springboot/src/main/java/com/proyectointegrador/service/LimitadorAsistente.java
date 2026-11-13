package com.proyectointegrador.service;

import com.proyectointegrador.exception.LimiteAsistenteException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.LongSupplier;

/**
 * Fase 5 - Límite de consultas a la IA por usuario (o por IP sin sesión) en una ventana de 60 s.
 * Protege el cupo gratuito de Groq; vive en memoria porque no necesita sobrevivir a un reinicio.
 */
@Component
public class LimitadorAsistente {

    private static final long VENTANA_MS = 60_000;
    private static final int MAX_CLAVES = 10_000;

    private final int limitePorMinuto;
    private final LongSupplier reloj;
    private final Map<String, Deque<Long>> consultas = new ConcurrentHashMap<>();

    @Autowired
    public LimitadorAsistente(@Value("${app.ia.limite-por-minuto:15}") int limitePorMinuto) {
        this(limitePorMinuto, System::currentTimeMillis);
    }

    LimitadorAsistente(int limitePorMinuto, LongSupplier reloj) {
        this.limitePorMinuto = Math.max(1, limitePorMinuto);
        this.reloj = reloj;
    }

    public void verificar(String clave) {
        long ahora = reloj.getAsLong();
        if (consultas.size() > MAX_CLAVES) limpiarVencidas(ahora);
        Deque<Long> historial = consultas.computeIfAbsent(clave, c -> new ArrayDeque<>());
        synchronized (historial) {
            while (!historial.isEmpty() && ahora - historial.peekFirst() >= VENTANA_MS) historial.pollFirst();
            if (historial.size() >= limitePorMinuto) {
                long segundos = Math.max(1, (historial.peekFirst() + VENTANA_MS - ahora + 999) / 1000);
                throw new LimiteAsistenteException(segundos);
            }
            historial.addLast(ahora);
        }
    }

    private void limpiarVencidas(long ahora) {
        consultas.entrySet().removeIf(e -> {
            synchronized (e.getValue()) {
                Long ultima = e.getValue().peekLast();
                return ultima == null || ahora - ultima >= VENTANA_MS;
            }
        });
    }
}
