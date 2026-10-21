package com.proyectointegrador.dto;

import com.proyectointegrador.entity.Usuario;

/**
 * H19 - Vista PÚBLICA de un usuario: lo único que otra persona puede ver de él
 * (autor de una publicación y, más adelante, coincidencias y contacto).
 *
 * Reglas de privacidad (ver docs/privacidad-datos.md):
 * - Público: id, nombre e inicial del apellido ("Ana P.").
 * - Privado: correo (dato de contacto), apellido completo, rol, estado,
 *   contraseña/hash, código de verificación, intentos y bloqueo.
 *   El titular los ve en /api/perfil; el ADMIN, solo en la API de Django.
 *
 * Construir siempre con {@link #publico(Usuario)}: nunca a mano con datos del usuario.
 */
public record UsuarioResumen(Long id, String nombre, String apellido) {

    public static UsuarioResumen publico(Usuario usuario) {
        if (usuario == null) return null;
        return new UsuarioResumen(usuario.getId(), usuario.getNombre(), inicial(usuario.getApellido()));
    }

    /** "Pérez" -> "P."; vacío o nulo -> "". */
    static String inicial(String apellido) {
        if (apellido == null || apellido.isBlank()) return "";
        String limpio = apellido.strip();
        return limpio.substring(0, limpio.offsetByCodePoints(0, 1)).toUpperCase() + ".";
    }
}
