package com.proyectointegrador.service;

import com.proyectointegrador.dto.AuthResponse;
import com.proyectointegrador.dto.LoginRequest;
import com.proyectointegrador.dto.RegisterRequest;
import com.proyectointegrador.dto.UsuarioResponse;
import com.proyectointegrador.entity.EstadoUsuario;
import com.proyectointegrador.entity.Rol;
import com.proyectointegrador.entity.Usuario;
import com.proyectointegrador.exception.DuplicateResourceException;
import com.proyectointegrador.exception.InvalidCredentialsException;
import com.proyectointegrador.repository.UsuarioRepository;
import com.proyectointegrador.security.JwtService;
import com.proyectointegrador.security.UserPrincipal;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AuthService {

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    public AuthService(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
    }

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (usuarioRepository.existsByCorreo(request.correo())) {
            throw new DuplicateResourceException("El correo ya está registrado");
        }

        Usuario usuario = new Usuario();
        usuario.setNombre(request.nombre());
        usuario.setApellido(request.apellido());
        usuario.setCorreo(request.correo());
        usuario.setPassword(passwordEncoder.encode(request.password()));
        usuario.setRol(Rol.USUARIO);
        usuario.setEstado(EstadoUsuario.ACTIVO);

        usuarioRepository.save(usuario);

        String token = jwtService.generateToken(UserPrincipal.from(usuario));
        return new AuthResponse(token, UsuarioResponse.from(usuario));
    }

    @Transactional(readOnly = true)
    public AuthResponse login(LoginRequest request) {
        Usuario usuario = usuarioRepository.findByCorreo(request.correo())
                .orElseThrow(() -> new InvalidCredentialsException("Credenciales inválidas"));

        if (!passwordEncoder.matches(request.password(), usuario.getPassword())) {
            throw new InvalidCredentialsException("Credenciales inválidas");
        }

        if (usuario.getEstado() != EstadoUsuario.ACTIVO) {
            throw new InvalidCredentialsException("Cuenta suspendida");
        }

        String token = jwtService.generateToken(UserPrincipal.from(usuario));
        return new AuthResponse(token, UsuarioResponse.from(usuario));
    }
}
