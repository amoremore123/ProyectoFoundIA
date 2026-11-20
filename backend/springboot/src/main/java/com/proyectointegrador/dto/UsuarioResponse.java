package com.proyectointegrador.dto;

import com.proyectointegrador.entity.Usuario;

import java.time.LocalDateTime;

/**
 * Datos de la propia cuenta (perfil y respuesta de inicio de sesión). No se usa en
 * respuestas públicas: para eso está {@link UsuarioResumen}.
 */
public record UsuarioResponse(
        Long id,
        String nombre,
        String apellido,
        String correo,
        String rol,
        String estado,
        LocalDateTime fechaRegistro,
        String fotoPerfilUrl
) {

    /** Compatibilidad con el contrato anterior a H64 (sin foto de perfil). */
    public UsuarioResponse(Long id, String nombre, String apellido, String correo, String rol, String estado,
                           LocalDateTime fechaRegistro) {
        this(id, nombre, apellido, correo, rol, estado, fechaRegistro, null);
    }

    public static UsuarioResponse from(Usuario usuario) {
        return new UsuarioResponse(
                usuario.getId(),
                usuario.getNombre(),
                usuario.getApellido(),
                usuario.getCorreo(),
                usuario.getRol().name(),
                usuario.getEstado().name(),
                usuario.getFechaRegistro(),
                urlFotoPerfil(usuario.getFotoPerfilUrl())
        );
    }

    /**
     * H64: la foto se sirve solo con sesión en {@code /api/perfil/foto}; {@code v} cambia con
     * cada foto (primeros caracteres de su nombre) para que los clientes no usen la anterior en caché.
     */
    public static String urlFotoPerfil(String rutaInterna) {
        if (rutaInterna == null || rutaInterna.isBlank()) {
            return null;
        }
        String nombre = rutaInterna.substring(rutaInterna.lastIndexOf('/') + 1);
        return "/api/perfil/foto?v=" + nombre.substring(0, Math.min(8, nombre.length()));
    }
}
