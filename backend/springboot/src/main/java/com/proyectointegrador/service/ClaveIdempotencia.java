package com.proyectointegrador.service;

import com.proyectointegrador.exception.SolicitudInvalidaException;

import java.util.regex.Pattern;

/**
 * H65: encabezado {@code Idempotency-Key}. Lo genera el cliente al guardar un envío en su cola sin
 * conexión y lo repite en cada reintento; el servidor devuelve lo ya creado en vez de duplicarlo.
 */
public final class ClaveIdempotencia {

    public static final String ENCABEZADO = "Idempotency-Key";
    private static final Pattern FORMATO = Pattern.compile("[A-Za-z0-9_-]{8,64}");

    private ClaveIdempotencia() {
    }

    /** @return la clave lista para guardar, o {@code null} si no se envió */
    public static String validar(String clave) {
        if (clave == null || clave.isBlank()) return null;
        String limpia = clave.trim();
        if (!FORMATO.matcher(limpia).matches()) {
            throw new SolicitudInvalidaException(
                    "Idempotency-Key debe tener de 8 a 64 letras, números, guiones o guiones bajos.");
        }
        return limpia;
    }
}
