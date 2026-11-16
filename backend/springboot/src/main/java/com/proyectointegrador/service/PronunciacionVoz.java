package com.proyectointegrador.service;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Fase 6 - Ajustes de pronunciación para la voz de FINDI. Solo cambian el texto que recibe el
 * servicio de voz; lo que se muestra en pantalla sigue bien escrito.
 *
 * «Tecsup» no está en el diccionario del modelo y se leía como una palabra en inglés; escrito
 * «Tec-sup» se pronuncia tal como se escribe (elegido escuchando variantes, 16/11/2026).
 */
public final class PronunciacionVoz {

    private static final Map<Pattern, String> REEMPLAZOS = new LinkedHashMap<>();

    static {
        REEMPLAZOS.put(Pattern.compile("\\bTecsup\\b", Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE), "Tec-sup");
    }

    private PronunciacionVoz() {
    }

    public static String paraVoz(String texto) {
        if (texto == null) return "";
        String salida = texto;
        for (Map.Entry<Pattern, String> r : REEMPLAZOS.entrySet()) {
            salida = r.getKey().matcher(salida).replaceAll(r.getValue());
        }
        return salida;
    }
}
