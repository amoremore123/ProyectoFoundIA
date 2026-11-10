package com.proyectointegrador.service;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Fase 5 - Quita datos personales de la frase antes de enviarla a la IA externa.
 * La IA solo necesita qué objeto es y dónde; correos, teléfonos o documentos no aportan.
 */
public final class LimpiadorDatosPersonales {

    static final String OMITIDO = "[dato omitido]";

    private static final List<Pattern> PATRONES = List.of(
            // Correos
            Pattern.compile("[\\w.+-]+@[\\w-]+(\\.[\\w-]+)+"),
            // Celulares de Perú (con o sin +51, con espacios o guiones)
            Pattern.compile("(\\+?51[\\s-]?)?\\b9\\d{2}[\\s-]?\\d{3}[\\s-]?\\d{3}\\b"),
            // Códigos de alumno de Tecsup (U seguido de dígitos)
            Pattern.compile("\\b[Uu]\\d{6,}\\b"),
            // DNI (8 dígitos) y cualquier número largo (tarjetas, cuentas)
            Pattern.compile("\\b\\d{8,}\\b"));

    private LimpiadorDatosPersonales() {
    }

    public static String limpiar(String texto) {
        if (texto == null) return "";
        String limpio = texto;
        for (Pattern patron : PATRONES) {
            limpio = patron.matcher(limpio).replaceAll(OMITIDO);
        }
        return limpio;
    }
}
