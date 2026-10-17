package com.proyectointegrador.service;

import com.proyectointegrador.dto.ObjetoResponse;
import com.proyectointegrador.dto.PerfilUpdateRequest;
import com.proyectointegrador.dto.UsuarioResponse;
import com.proyectointegrador.entity.Usuario;
import com.proyectointegrador.exception.PasswordActualIncorrectaException;
import com.proyectointegrador.exception.ResourceNotFoundException;
import com.proyectointegrador.repository.UsuarioRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class PerfilService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final ObjetoService objetoService;

    public PerfilService(UsuarioRepository usuarioRepository,
                         PasswordEncoder passwordEncoder,
                         ObjetoService objetoService) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.objetoService = objetoService;
    }

    @Transactional(readOnly = true)
    public UsuarioResponse obtener(Long usuarioId) {
        return UsuarioResponse.from(buscarUsuario(usuarioId));
    }

    /**
     * H13 - Actualiza solo el perfil del usuario del token. Si se cambia la
     * contraseña, primero se comprueba la actual; si no coincide no se guarda nada.
     */
    @Transactional
    public UsuarioResponse actualizar(Long usuarioId, PerfilUpdateRequest request) {
        Usuario usuario = buscarUsuario(usuarioId);

        if (request.cambiaPassword()) {
            String actual = request.passwordActual();
            if (actual == null || actual.isEmpty() || !passwordEncoder.matches(actual, usuario.getPassword())) {
                throw new PasswordActualIncorrectaException(
                        "La contraseña actual no es correcta. No se guardaron los cambios.");
            }
        }

        usuario.setNombre(request.nombre().trim());
        usuario.setApellido(request.apellido().trim());
        if (request.cambiaPassword()) {
            usuario.setPassword(passwordEncoder.encode(request.password()));
        }
        usuarioRepository.save(usuario);
        return UsuarioResponse.from(usuario);
    }

    @Transactional(readOnly = true)
    public List<ObjetoResponse> objetos(Long usuarioId) {
        buscarUsuario(usuarioId);
        return objetoService.porUsuario(usuarioId);
    }

    private Usuario buscarUsuario(Long usuarioId) {
        return usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con id " + usuarioId));
    }
}
