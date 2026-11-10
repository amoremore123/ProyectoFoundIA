package com.proyectointegrador.security;

import com.proyectointegrador.exception.LimiteAsistenteException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;

/**
 * H45: límite simple por usuario o IP para que el asistente no se use como
 * puerta abierta al proveedor de IA. Es una ventana fija de un minuto.
 */
@Component
public class AsistenteRateLimiter {

    private static final long VENTANA_MS = 60_000;

    private final int limite;
    private final ConcurrentHashMap<String, Ventana> ventanas = new ConcurrentHashMap<>();

    public AsistenteRateLimiter(@Value("${app.asistente.limite-por-minuto:20}") int limite) {
        this.limite = Math.max(1, limite);
    }

    public void verificar(String clave) {
        long ahora = System.currentTimeMillis();
        Ventana ventana = ventanas.compute(clave, (k, actual) -> {
            if (actual == null || ahora - actual.inicio >= VENTANA_MS) {
                return new Ventana(ahora, 1);
            }
            return new Ventana(actual.inicio, actual.contador + 1);
        });

        if (ventana.contador > limite) {
            long segundos = Math.max(1, (ventana.inicio + VENTANA_MS - ahora + 999) / 1000);
            throw new LimiteAsistenteException(
                    "Demasiadas consultas al asistente; espera unos segundos e inténtalo de nuevo.",
                    segundos);
        }
    }

    /** Solo para pruebas: reinicia las ventanas en memoria. */
    public void limpiar() {
        ventanas.clear();
    }

    private record Ventana(long inicio, int contador) {
    }
}
