package com.proyectointegrador.service;

import com.proyectointegrador.dto.MensajeResponse;
import com.proyectointegrador.dto.RecuperarPasswordRequest;
import com.proyectointegrador.dto.RestablecerPasswordRequest;
import com.proyectointegrador.entity.EstadoUsuario;
import com.proyectointegrador.entity.TokenRecuperacionPassword;
import com.proyectointegrador.entity.Usuario;
import com.proyectointegrador.exception.EnvioCorreoException;
import com.proyectointegrador.exception.SolicitudInvalidaException;
import com.proyectointegrador.repository.TokenRecuperacionPasswordRepository;
import com.proyectointegrador.repository.UsuarioRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;

/**
 * H35 - Recuperar contraseña.
 * <ul>
 *   <li>La solicitud responde siempre lo mismo: no revela qué correos tienen cuenta.</li>
 *   <li>El enlace es de un solo uso, vence en 30 minutos y solo se guarda su hash.</li>
 *   <li>Cambiar la contraseña no reactiva cuentas suspendidas ni quita bloqueos de login,
 *       e invalida las sesiones (JWT) abiertas antes del cambio.</li>
 * </ul>
 */
@Service
public class RecuperacionPasswordService {

    public static final long MINUTOS_VIGENCIA = 30;
    /** Espera mínima entre dos correos de recuperación a la misma cuenta. */
    public static final long SEGUNDOS_ESPERA = 60;

    static final String MENSAJE_SOLICITUD =
            "Si el correo está registrado, te enviamos un enlace para restablecer tu contraseña.";
    static final String MENSAJE_ENLACE_INVALIDO =
            "El enlace no es válido o ya venció. Solicita uno nuevo.";
    static final String MENSAJE_CAMBIO =
            "Tu contraseña se actualizó. Inicia sesión con tu nueva contraseña.";

    private static final Logger log = LoggerFactory.getLogger(RecuperacionPasswordService.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UsuarioRepository usuarioRepository;
    private final TokenRecuperacionPasswordRepository tokenRepository;
    private final PasswordEncoder passwordEncoder;
    private final EmailService emailService;
    private final String webUrl;
    private final Clock clock;

    @Autowired
    public RecuperacionPasswordService(UsuarioRepository usuarioRepository,
                                       TokenRecuperacionPasswordRepository tokenRepository,
                                       PasswordEncoder passwordEncoder,
                                       EmailService emailService,
                                       @Value("${app.web.url:http://localhost:5173}") String webUrl) {
        this(usuarioRepository, tokenRepository, passwordEncoder, emailService, webUrl, Clock.systemUTC());
    }

    /** Constructor para pruebas: permite fijar la hora. */
    RecuperacionPasswordService(UsuarioRepository usuarioRepository,
                                TokenRecuperacionPasswordRepository tokenRepository,
                                PasswordEncoder passwordEncoder,
                                EmailService emailService,
                                String webUrl,
                                Clock clock) {
        this.usuarioRepository = usuarioRepository;
        this.tokenRepository = tokenRepository;
        this.passwordEncoder = passwordEncoder;
        this.emailService = emailService;
        this.webUrl = webUrl.endsWith("/") ? webUrl.substring(0, webUrl.length() - 1) : webUrl;
        this.clock = clock;
    }

    // --------------------------------------------------------- solicitar

    @Transactional
    public MensajeResponse solicitar(RecuperarPasswordRequest request) {
        String correo = request.correo().trim().toLowerCase(Locale.ROOT);
        Usuario usuario = usuarioRepository.findByCorreoForUpdate(correo).orElse(null);

        // Sin cuenta, cuenta suspendida o pedido demasiado seguido: misma respuesta y sin correo.
        if (usuario == null || usuario.getEstado() != EstadoUsuario.ACTIVO || esperaPendiente(usuario)) {
            return new MensajeResponse(MENSAJE_SOLICITUD);
        }

        LocalDateTime ahora = ahora();
        invalidarEnlacesAnteriores(usuario, ahora);

        String token = generarToken();
        TokenRecuperacionPassword registro = new TokenRecuperacionPassword();
        registro.setUsuario(usuario);
        registro.setTokenHash(hash(token));
        registro.setCreadoEn(ahora);
        registro.setExpiraEn(ahora.plusMinutes(MINUTOS_VIGENCIA));
        tokenRepository.save(registro);

        String enlace = webUrl + "/restablecer-password?token=" + token;
        try {
            emailService.enviarEnlaceRecuperacion(usuario.getCorreo(), usuario.getNombre(), enlace, MINUTOS_VIGENCIA);
        } catch (EnvioCorreoException ex) {
            // No se revela si la cuenta existe: el enlace que no llegó queda inutilizable.
            registro.setUsadoEn(ahora);
            log.warn("Falló el envío del enlace de recuperación del usuario {}", usuario.getId());
        }
        return new MensajeResponse(MENSAJE_SOLICITUD);
    }

    // ------------------------------------------------------- restablecer

    @Transactional(noRollbackFor = SolicitudInvalidaException.class)
    public MensajeResponse restablecer(RestablecerPasswordRequest request) {
        TokenRecuperacionPassword registro = tokenRepository.findByTokenHashForUpdate(hash(request.token()))
                .orElseThrow(() -> new SolicitudInvalidaException(MENSAJE_ENLACE_INVALIDO));

        LocalDateTime ahora = ahora();
        if (registro.getUsadoEn() != null || !ahora.isBefore(registro.getExpiraEn())) {
            throw new SolicitudInvalidaException(MENSAJE_ENLACE_INVALIDO);
        }

        Usuario usuario = usuarioRepository.findByIdForUpdate(registro.getUsuario().getId())
                .orElseThrow(() -> new SolicitudInvalidaException(MENSAJE_ENLACE_INVALIDO));
        if (usuario.getEstado() != EstadoUsuario.ACTIVO) {
            // Una cuenta suspendida no se reactiva ni cambia su contraseña por esta vía.
            registro.setUsadoEn(ahora);
            throw new SolicitudInvalidaException(MENSAJE_ENLACE_INVALIDO);
        }

        usuario.setPassword(passwordEncoder.encode(request.password()));
        usuario.setPasswordActualizadoEn(ahora);
        // Se conservan estado, verificación, intentos fallidos y bloqueo temporal.
        usuarioRepository.save(usuario);

        registro.setUsadoEn(ahora);
        invalidarEnlacesAnteriores(usuario, ahora);
        return new MensajeResponse(MENSAJE_CAMBIO);
    }

    // -------------------------------------------------------- utilidades

    private boolean esperaPendiente(Usuario usuario) {
        return tokenRepository.findFirstByUsuarioIdOrderByCreadoEnDesc(usuario.getId())
                .map(ultimo -> Duration.between(ultimo.getCreadoEn(), ahora()).getSeconds() < SEGUNDOS_ESPERA)
                .orElse(false);
    }

    private void invalidarEnlacesAnteriores(Usuario usuario, LocalDateTime ahora) {
        for (TokenRecuperacionPassword anterior : tokenRepository.findByUsuarioIdAndUsadoEnIsNull(usuario.getId())) {
            anterior.setUsadoEn(ahora);
        }
    }

    private static String generarToken() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException ex) {
            throw new IllegalStateException("SHA-256 no disponible", ex);
        }
    }

    private LocalDateTime ahora() {
        return LocalDateTime.now(clock);
    }
}
