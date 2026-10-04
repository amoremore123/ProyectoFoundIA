package com.proyectointegrador.dto;

import com.proyectointegrador.entity.Usuario;

import java.time.LocalDateTime;

public record UsuarioResponse(
        Long id,
        String nombre,
        String apellido,
        String correo,
        String rol,
        String estado,
        LocalDateTime fechaRegistro
) {

    public static UsuarioResponse from(Usuario usuario) {
        return new UsuarioResponse(
                usuario.getId(),
                usuario.getNombre(),
                usuario.getApellido(),
                usuario.getCorreo(),
                usuario.getRol().name(),
                usuario.getEstado().name(),
                usuario.getFechaRegistro()
        );
    }
}
