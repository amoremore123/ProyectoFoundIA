package com.proyectointegrador.service;

import com.proyectointegrador.dto.AuthResponse;
import com.proyectointegrador.dto.LoginRequest;
import com.proyectointegrador.dto.MensajeResponse;
import com.proyectointegrador.dto.ReenviarCodigoRequest;
import com.proyectointegrador.dto.RegisterRequest;
import com.proyectointegrador.dto.RegistroResponse;
import com.proyectointegrador.dto.UsuarioResponse;
import com.proyectointegrador.dto.VerificarRequest;
import com.proyectointegrador.entity.EstadoUsuario;
import com.proyectointegrador.entity.Rol;
import com.proyectointegrador.entity.Usuario;
import com.proyectointegrador.exception.CodigoVerificacionException;
import com.proyectointegrador.exception.CuentaBloqueadaException;
import com.proyectointegrador.exception.CuentaNoVerificadaException;
import com.proyectointegrador.exception.DuplicateResourceException;
import com.proyectointegrador.exception.InvalidCredentialsException;
import com.proyectointegrador.repository.UsuarioRepository;
import com.proyectointegrador.security.JwtService;
import com.proyectointegrador.security.UserPrincipal;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Locale;

/**
 * H11 (registro + verificación por correo) y H12 (login con JWT y bloqueo tras 5 intentos).
 */
@Service
public class AuthService {

    public static final int MAX_INTENTOS = 5;
    public static final long MINUTOS_BLOQUEO = 15;
    public static final long MINUTOS_VIGENCIA_CODIGO = 15;

    private static final String MENSAJE_CREDENCIALES = "Correo o contraseña incorrectos.";
    private static final String MENSAJE_CUENTA_SUSPENDIDA = "Tu cuenta está suspendida. Contacta al administrador.";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UsuarioRepository usuarioRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final EmailService emailService;
    private final Clock clock;

    @Autowired
    public AuthService(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder,
                       JwtService jwtService, EmailService emailService) {
        this(usuarioRepository, passwordEncoder, jwtService, emailService, Clock.systemDefaultZone());
    }

    /** Constructor para pruebas: permite fijar la hora. */
    AuthService(UsuarioRepository usuarioRepository, PasswordEncoder passwordEncoder,
                JwtService jwtService, EmailService emailService, Clock clock) {
        this.usuarioRepository = usuarioRepository;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.emailService = emailService;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ H11

    @Transactional
    public RegistroResponse register(RegisterRequest request) {
        String correo = normalizarCorreo(request.correo());
        if (usuarioRepository.existsByCorreo(correo)) {
            throw new DuplicateResourceException("El correo ya está registrado");
        }

        Usuario usuario = new Usuario();
        usuario.setNombre(request.nombre().trim());
        usuario.setApellido(request.apellido().trim());
        usuario.setCorreo(correo);
        usuario.setPassword(passwordEncoder.encode(request.password()));
        usuario.setRol(Rol.USUARIO);
        usuario.setEstado(EstadoUsuario.ACTIVO);
        usuario.setVerificado(false);
        asignarNuevoCodigo(usuario);

        usuarioRepository.save(usuario);
        // Si el correo falla se lanza EnvioCorreoException y la transacción se revierte.
        emailService.enviarCodigoVerificacion(
                correo, usuario.getNombre(), usuario.getCodigoVerificacion(), MINUTOS_VIGENCIA_CODIGO);

        return new RegistroResponse(
                "Te enviamos un código de verificación a " + correo + ". Ingrésalo para activar tu cuenta.",
                correo);
    }

    @Transactional
    public AuthResponse verificar(VerificarRequest request) {
        Usuario usuario = usuarioRepository.findByCorreoForUpdate(normalizarCorreo(request.correo()))
                .orElseThrow(() -> new CodigoVerificacionException("Código incorrecto."));

        if (usuario.getEstado() != EstadoUsuario.ACTIVO) {
            throw new InvalidCredentialsException(MENSAJE_CUENTA_SUSPENDIDA);
        }
        if (usuario.isVerificado()) {
            throw new CodigoVerificacionException("Tu cuenta ya está verificada. Inicia sesión.");
        }
        if (usuario.getCodigoVerificacion() == null || usuario.getCodigoExpira() == null
                || ahora().isAfter(usuario.getCodigoExpira())) {
            throw new CodigoVerificacionException("El código expiró. Solicita uno nuevo.");
        }
        if (!codigosIguales(usuario.getCodigoVerificacion(), request.codigo().trim())) {
            throw new CodigoVerificacionException("Código incorrecto.");
        }

        usuario.setVerificado(true);
        usuario.setCodigoVerificacion(null);
        usuario.setCodigoExpira(null);
        usuarioRepository.save(usuario);

        return crearRespuestaConToken(usuario);
    }

    @Transactional
    public MensajeResponse reenviarCodigo(ReenviarCodigoRequest request) {
        String correo = normalizarCorreo(request.correo());
        Usuario usuario = usuarioRepository.findByCorreoForUpdate(correo).orElse(null);

        if (usuario != null && usuario.isVerificado()) {
            throw new CodigoVerificacionException("Tu cuenta ya está verificada. Inicia sesión.");
        }
        if (usuario != null) {
            asignarNuevoCodigo(usuario);
            usuarioRepository.save(usuario);
            emailService.enviarCodigoVerificacion(
                    correo, usuario.getNombre(), usuario.getCodigoVerificacion(), MINUTOS_VIGENCIA_CODIGO);
        }
        // Mismo mensaje exista o no el correo, para no revelar qué cuentas existen.
        return new MensajeResponse("Si el correo está registrado, te enviamos un nuevo código.");
    }

    // ------------------------------------------------------------------ H12

    /**
     * noRollbackFor: el contador de intentos y el bloqueo deben guardarse
     * aunque el método termine lanzando la excepción.
     */
    @Transactional(noRollbackFor = {InvalidCredentialsException.class, CuentaBloqueadaException.class})
    public AuthResponse login(LoginRequest request) {
        // Serializa los intentos de esta cuenta para no perder incrementos concurrentes.
        Usuario usuario = usuarioRepository.findByCorreoForUpdate(normalizarCorreo(request.correo()))
                .orElseThrow(() -> new InvalidCredentialsException(MENSAJE_CREDENCIALES));

        LocalDateTime ahora = ahora();

        if (estaBloqueado(usuario, ahora)) {
            throw new CuentaBloqueadaException(mensajeBloqueo(usuario.getBloqueadoHasta(), ahora));
        }

        if (!passwordEncoder.matches(request.password(), usuario.getPassword())) {
            registrarIntentoFallido(usuario, ahora);
        }

        if (usuario.getEstado() != EstadoUsuario.ACTIVO) {
            throw new InvalidCredentialsException(MENSAJE_CUENTA_SUSPENDIDA);
        }
        if (!usuario.isVerificado()) {
            throw new CuentaNoVerificadaException("Debes verificar tu correo antes de iniciar sesión.");
        }

        if (usuario.getIntentosFallidos() != 0 || usuario.getBloqueadoHasta() != null) {
            usuario.setIntentosFallidos(0);
            usuario.setBloqueadoHasta(null);
            usuarioRepository.save(usuario);
        }

        return crearRespuestaConToken(usuario);
    }

    /** Siempre lanza excepción: 401 con intentos restantes, o 423 al llegar al máximo. */
    private void registrarIntentoFallido(Usuario usuario, LocalDateTime ahora) {
        int intentos = usuario.getIntentosFallidos() + 1;

        if (intentos >= MAX_INTENTOS) {
            usuario.setIntentosFallidos(0);
            usuario.setBloqueadoHasta(ahora.plusMinutes(MINUTOS_BLOQUEO));
            usuarioRepository.save(usuario);
            throw new CuentaBloqueadaException("Superaste los " + MAX_INTENTOS
                    + " intentos permitidos. Tu cuenta quedó bloqueada por " + MINUTOS_BLOQUEO + " minutos.");
        }

        usuario.setIntentosFallidos(intentos);
        usuarioRepository.save(usuario);
        int restantes = MAX_INTENTOS - intentos;
        throw new InvalidCredentialsException(MENSAJE_CREDENCIALES + " Te queda" + (restantes == 1 ? "" : "n")
                + " " + restantes + " intento" + (restantes == 1 ? "" : "s") + ".");
    }

    // ------------------------------------------------------------ utilidades

    private boolean estaBloqueado(Usuario usuario, LocalDateTime ahora) {
        return usuario.getBloqueadoHasta() != null && ahora.isBefore(usuario.getBloqueadoHasta());
    }

    private String mensajeBloqueo(LocalDateTime hasta, LocalDateTime ahora) {
        long segundos = Duration.between(ahora, hasta).getSeconds();
        long minutos = Math.max(1, (segundos + 59) / 60);
        return "Tu cuenta está bloqueada por demasiados intentos fallidos. Intenta de nuevo en "
                + minutos + (minutos == 1 ? " minuto." : " minutos.");
    }

    private void asignarNuevoCodigo(Usuario usuario) {
        usuario.setCodigoVerificacion(String.format("%06d", RANDOM.nextInt(1_000_000)));
        usuario.setCodigoExpira(ahora().plusMinutes(MINUTOS_VIGENCIA_CODIGO));
    }

    private boolean codigosIguales(String esperado, String recibido) {
        return MessageDigest.isEqual(
                esperado.getBytes(StandardCharsets.UTF_8), recibido.getBytes(StandardCharsets.UTF_8));
    }

    private AuthResponse crearRespuestaConToken(Usuario usuario) {
        String token = jwtService.generateToken(UserPrincipal.from(usuario));
        return new AuthResponse(token, UsuarioResponse.from(usuario));
    }

    private LocalDateTime ahora() {
        return LocalDateTime.now(clock);
    }

    static String normalizarCorreo(String correo) {
        return correo == null ? null : correo.trim().toLowerCase(Locale.ROOT);
    }
}
