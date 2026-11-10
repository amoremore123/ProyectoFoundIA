package com.proyectointegrador.service;

import java.text.Normalizer;
import java.util.Locale;
import java.util.Set;

/**
 * H45: vocabulario compartido por el motor local y el validador de la respuesta de IA.
 * No contiene datos de usuarios; solo listas de palabras del dominio.
 */
public final class AsistenteLexico {

    public static final Set<String> INTENCIONES = Set.of(
            "buscar", "publicar", "ayuda", "saludo", "gracias", "vacio");

    public static final Set<String> TIPOS = Set.of("", "PERDIDO", "ENCONTRADO");

    public static final Set<String> COLORES = Set.of(
            "negro", "negra", "blanco", "blanca", "azul", "rojo", "roja", "verde",
            "amarillo", "amarilla", "gris", "rosado", "rosada", "morado", "morada",
            "marron", "cafe", "plateado", "plateada", "dorado", "dorada", "celeste",
            "naranja", "beige");

    private AsistenteLexico() {
    }

    /** Minúsculas, sin tildes y sin signos: igual que el motor del frontend. */
    public static String normalizar(String texto) {
        if (texto == null) {
            return "";
        }
        String sinTildes = Normalizer.normalize(texto.toLowerCase(Locale.ROOT), Normalizer.Form.NFD)
                .replaceAll("\\p{M}+", "");
        return sinTildes.replaceAll("[^a-z0-9ñ\\s]", " ")
                .replaceAll("\\s+", " ")
                .trim();
    }

    /** Singular aproximado: "llaves" -> "llave", "celulares" -> "celular". */
    public static String raiz(String palabra) {
        if (palabra == null || palabra.isBlank()) {
            return "";
        }
        if (palabra.length() > 5 && palabra.matches(".*[lrndjzy]es")) {
            return palabra.substring(0, palabra.length() - 2);
        }
        if (palabra.length() > 3 && palabra.endsWith("s")) {
            return palabra.substring(0, palabra.length() - 1);
        }
        return palabra;
    }

    public static boolean esColor(String palabra) {
        return COLORES.contains(palabra) || COLORES.contains(palabra + "s");
    }
}
