package com.proyectointegrador.service;

import com.proyectointegrador.exception.AsistenteNoDisponibleException;
import com.proyectointegrador.exception.LimiteAsistenteException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZonedDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.HashMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;

/**
 * Fase 6 - Voz natural de FINDI. Cuida el cupo gratuito de ElevenLabs con tres medidas:
 * caché de frases repetidas (no gastan caracteres), límite por minuto por persona y tope diario
 * de caracteres. Si algo falla responde 503/429 y la web usa la voz del navegador.
 */
@Service
public class VozService {

    private static final Logger log = LoggerFactory.getLogger(VozService.class);
    private static final int MAX_EN_CACHE = 60;

    private final ClienteVoz cliente;
    private final LimitadorAsistente limitador;
    private final int limiteCaracteresDia;
    private final Clock reloj;

    // Caché LRU: el saludo y las respuestas fijas se generan una sola vez
    private final Map<String, byte[]> cache = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, byte[]> mayor) {
            return size() > MAX_EN_CACHE;
        }
    };
    // Las solicitudes simultáneas de la misma frase comparten una generación.
    private final Map<String, CompletableFuture<byte[]>> pendientes = new HashMap<>();
    private LocalDate dia;
    private int caracteresHoy;

    @Autowired
    public VozService(ClienteVoz cliente,
                      @Value("${app.voz.limite-por-minuto:10}") int limitePorMinuto,
                      @Value("${app.voz.limite-caracteres-dia:2000}") int limiteCaracteresDia) {
        this(cliente, new LimitadorAsistente(limitePorMinuto), limiteCaracteresDia, Clock.systemUTC());
    }

    VozService(ClienteVoz cliente, LimitadorAsistente limitador, int limiteCaracteresDia, Clock reloj) {
        this.cliente = cliente;
        this.limitador = limitador;
        this.limiteCaracteresDia = Math.max(0, limiteCaracteresDia);
        this.reloj = reloj;
    }

    public byte[] sintetizar(String texto, String claveLimite) {
        if (!cliente.configurado()) {
            throw new AsistenteNoDisponibleException("La voz natural de FINDI no está configurada en el servidor.");
        }
        // Solo se envía lo que dice FINDI, con el mismo limpiador de datos personales
        String limpio = LimpiadorDatosPersonales.limpiar(texto.trim());

        CompletableFuture<byte[]> pendiente;
        boolean generar;
        synchronized (cache) {
            byte[] guardado = cache.get(limpio);
            if (guardado != null) return guardado;
            pendiente = pendientes.get(limpio);
            generar = pendiente == null;
            if (generar) {
                pendiente = new CompletableFuture<>();
                pendientes.put(limpio, pendiente);
            }
        }
        if (!generar) {
            try {
                return pendiente.join();
            } catch (CompletionException e) {
                if (e.getCause() instanceof RuntimeException causa) throw causa;
                throw e;
            }
        }

        try {
            byte[] audio = generarAudio(limpio, claveLimite);
            pendiente.complete(audio);
            return audio;
        } catch (RuntimeException e) {
            pendiente.completeExceptionally(e);
            throw e;
        } finally {
            synchronized (cache) {
                pendientes.remove(limpio);
            }
        }
    }

    private byte[] generarAudio(String limpio, String claveLimite) {
        limitador.verificar(claveLimite);
        reservarCaracteres(limpio.length());

        long inicio = System.nanoTime();
        try {
            byte[] audio = cliente.sintetizar(limpio);
            if (audio == null || audio.length == 0) throw new IllegalStateException("audio vacío");
            synchronized (cache) {
                cache.put(limpio, audio);
            }
            // Registro técnico: tamaño y duración, nunca el texto ni la clave
            log.info("FINDI voz: audio generado ({} caracteres, {} ms)", limpio.length(), milis(inicio));
            return audio;
        } catch (RuntimeException e) {
            devolverCaracteres(limpio.length());
            log.warn("FINDI voz: el servicio falló ({}, {} ms)", e.getClass().getSimpleName(), milis(inicio));
            throw new AsistenteNoDisponibleException("La voz natural de FINDI no respondió. Se usará la voz del navegador.");
        }
    }

    private synchronized void reservarCaracteres(int cantidad) {
        LocalDate hoy = LocalDate.now(reloj);
        if (!hoy.equals(dia)) {
            dia = hoy;
            caracteresHoy = 0;
        }
        if (caracteresHoy + cantidad > limiteCaracteresDia) {
            ZonedDateTime ahora = ZonedDateTime.now(reloj);
            long segundos = Math.max(1, Duration.between(ahora, ahora.toLocalDate().plusDays(1).atStartOfDay(ahora.getZone())).toSeconds());
            throw new LimiteAsistenteException(segundos);
        }
        caracteresHoy += cantidad;
    }

    private synchronized void devolverCaracteres(int cantidad) {
        caracteresHoy = Math.max(0, caracteresHoy - cantidad);
    }

    private static long milis(long inicioNano) {
        return (System.nanoTime() - inicioNano) / 1_000_000;
    }
}
