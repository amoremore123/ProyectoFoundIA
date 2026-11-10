package com.proyectointegrador.service;

import com.proyectointegrador.entity.TokenRevocado;
import com.proyectointegrador.entity.Usuario;
import com.proyectointegrador.repository.TokenRevocadoRepository;
import com.proyectointegrador.repository.UsuarioRepository;
import com.proyectointegrador.security.JwtService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Date;

/**
 * H37: cierre de sesión de un token o de todos los dispositivos.
 *
 * <p>El cierre individual guarda el {@code jti} en {@code tokens_revocados}
 * hasta su expiración. El cierre global incrementa
 * {@code usuarios.sesion_version}; cualquier JWT con una versión anterior
 * deja de ser válido, aunque no se conozca su {@code jti}.</p>
 */
@Service
public class CierreSesionService {

    private static final long HORAS_RESPALDO = 12;

    private final TokenRevocadoRepository tokenRevocadoRepository;
    private final UsuarioRepository usuarioRepository;
    private final JwtService jwtService;
    private final Clock clock;

    @Autowired
    public CierreSesionService(TokenRevocadoRepository tokenRevocadoRepository,
                               UsuarioRepository usuarioRepository,
                               JwtService jwtService) {
        this(tokenRevocadoRepository, usuarioRepository, jwtService, Clock.systemUTC());
    }

    /** Constructor para pruebas: permite fijar la hora. */
    CierreSesionService(TokenRevocadoRepository tokenRevocadoRepository,
                        UsuarioRepository usuarioRepository,
                        JwtService jwtService,
                        Clock clock) {
        this.tokenRevocadoRepository = tokenRevocadoRepository;
        this.usuarioRepository = usuarioRepository;
        this.jwtService = jwtService;
        this.clock = clock;
    }

    /**
     * Revoca el token actual. Si es un JWT legado sin {@code jti}, no hay
     * forma segura de revocar solo ese token: se cierran todas las sesiones.
     *
     * @return {@code true} si se cerraron todas las sesiones de la cuenta
     */
    @Transactional
    public boolean cerrarSesion(Long usuarioId, String token) {
        Usuario usuario = usuarioRepository.findByIdForUpdate(usuarioId)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado"));

        String jti = jwtService.extractJti(token);
        if (jti == null) {
            cerrarTodasLasSesiones(usuario);
            return true;
        }

        Date expiracion = jwtService.extractExpiration(token);
        LocalDateTime expiraEn = expiracion == null
                ? ahora().plusHours(HORAS_RESPALDO)
                : LocalDateTime.ofInstant(expiracion.toInstant(), ZoneOffset.UTC);

        // Las revocaciones vencidas ya no protegen nada y se pueden borrar.
        tokenRevocadoRepository.deleteByExpiraEnBefore(ahora());
        tokenRevocadoRepository.save(new TokenRevocado(jti, usuario, ahora(), expiraEn));
        return false;
    }

    /** Invalida todos los JWT emitidos hasta este momento para la cuenta. */
    @Transactional
    public void cerrarTodasLasSesiones(Long usuarioId) {
        Usuario usuario = usuarioRepository.findByIdForUpdate(usuarioId)
                .orElseThrow(() -> new UsernameNotFoundException("Usuario no encontrado"));
        cerrarTodasLasSesiones(usuario);
    }

    public boolean estaRevocado(String jti) {
        return jti != null && !jti.isBlank() && tokenRevocadoRepository.existsByJti(jti);
    }

    private void cerrarTodasLasSesiones(Usuario usuario) {
        usuario.setSesionVersion(usuario.getSesionVersion() + 1);
        usuarioRepository.save(usuario);
    }

    private LocalDateTime ahora() {
        return LocalDateTime.now(clock).withNano(0);
    }
}
