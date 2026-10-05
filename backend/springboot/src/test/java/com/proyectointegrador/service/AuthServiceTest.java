package com.proyectointegrador.service;

import com.proyectointegrador.dto.AuthResponse;
import com.proyectointegrador.dto.LoginRequest;
import com.proyectointegrador.dto.MensajeResponse;
import com.proyectointegrador.dto.ReenviarCodigoRequest;
import com.proyectointegrador.dto.RegisterRequest;
import com.proyectointegrador.dto.RegistroResponse;
import com.proyectointegrador.dto.VerificarRequest;
import com.proyectointegrador.entity.EstadoUsuario;
import com.proyectointegrador.entity.Rol;
import com.proyectointegrador.entity.Usuario;
import com.proyectointegrador.exception.CodigoVerificacionException;
import com.proyectointegrador.exception.CuentaBloqueadaException;
import com.proyectointegrador.exception.CuentaNoVerificadaException;
import com.proyectointegrador.exception.DuplicateResourceException;
import com.proyectointegrador.exception.EnvioCorreoException;
import com.proyectointegrador.exception.InvalidCredentialsException;
import com.proyectointegrador.repository.UsuarioRepository;
import com.proyectointegrador.security.JwtService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String PASSWORD = "Segura123!";
    private static final LocalDateTime AHORA = LocalDateTime.of(2026, 10, 5, 10, 0, 0);

    @Mock
    private UsuarioRepository usuarioRepository;
    @Mock
    private JwtService jwtService;
    @Mock
    private EmailService emailService;

    private final PasswordEncoder passwordEncoder = new BCryptPasswordEncoder(4); // rápido para tests
    private AuthService authService;

    @BeforeEach
    void setUp() {
        Clock reloj = Clock.fixed(AHORA.toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
        authService = new AuthService(usuarioRepository, passwordEncoder, jwtService, emailService, reloj);
        lenient().when(usuarioRepository.save(any(Usuario.class))).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(jwtService.generateToken(any())).thenReturn("jwt-de-prueba");
    }

    private Usuario usuario(boolean verificado) {
        Usuario u = new Usuario();
        u.setId(1L);
        u.setNombre("Ana");
        u.setApellido("Pérez");
        u.setCorreo("ana@foundia.dev");
        u.setPassword(passwordEncoder.encode(PASSWORD));
        u.setRol(Rol.USUARIO);
        u.setEstado(EstadoUsuario.ACTIVO);
        u.setVerificado(verificado);
        return u;
    }

    // ================================================================ H11
    @Nested
    @DisplayName("H11 - Registro y verificación")
    class Registro {

        private final RegisterRequest request =
                new RegisterRequest(" Ana ", "Pérez", "  Ana@FoundIA.dev ", PASSWORD);

        @Test
        @DisplayName("Registro válido: guarda hash BCrypt, queda sin verificar y envía código")
        void registroValido() {
            when(usuarioRepository.existsByCorreo("ana@foundia.dev")).thenReturn(false);

            RegistroResponse respuesta = authService.register(request);

            ArgumentCaptor<Usuario> captor = ArgumentCaptor.forClass(Usuario.class);
            verify(usuarioRepository).save(captor.capture());
            Usuario guardado = captor.getValue();

            assertThat(guardado.getCorreo()).isEqualTo("ana@foundia.dev");
            assertThat(guardado.getNombre()).isEqualTo("Ana");
            assertThat(guardado.getPassword()).isNotEqualTo(PASSWORD).startsWith("$2a$");
            assertThat(passwordEncoder.matches(PASSWORD, guardado.getPassword())).isTrue();
            assertThat(guardado.isVerificado()).isFalse();
            assertThat(guardado.getRol()).isEqualTo(Rol.USUARIO);
            assertThat(guardado.getCodigoVerificacion()).matches("\\d{6}");
            assertThat(guardado.getCodigoExpira())
                    .isEqualTo(AHORA.plusMinutes(AuthService.MINUTOS_VIGENCIA_CODIGO));
            verify(emailService).enviarCodigoVerificacion(
                    eq("ana@foundia.dev"), eq("Ana"), eq(guardado.getCodigoVerificacion()), anyLong());
            assertThat(respuesta.correo()).isEqualTo("ana@foundia.dev");
        }

        @Test
        @DisplayName("Correo duplicado: lanza 409 y no guarda nada")
        void correoDuplicado() {
            when(usuarioRepository.existsByCorreo("ana@foundia.dev")).thenReturn(true);

            assertThatThrownBy(() -> authService.register(request))
                    .isInstanceOf(DuplicateResourceException.class);
            verify(usuarioRepository, never()).save(any());
            verify(emailService, never()).enviarCodigoVerificacion(anyString(), anyString(), anyString(), anyLong());
        }

        @Test
        @DisplayName("Si el correo no se puede enviar, el registro falla (503)")
        void falloEnvioCorreo() {
            when(usuarioRepository.existsByCorreo(anyString())).thenReturn(false);
            doThrow(new EnvioCorreoException("sin SMTP")).when(emailService)
                    .enviarCodigoVerificacion(anyString(), anyString(), anyString(), anyLong());

            assertThatThrownBy(() -> authService.register(request)).isInstanceOf(EnvioCorreoException.class);
        }

        @Test
        @DisplayName("Código correcto: verifica la cuenta y devuelve JWT")
        void verificarCodigoCorrecto() {
            Usuario u = usuario(false);
            u.setCodigoVerificacion("123456");
            u.setCodigoExpira(AHORA.plusMinutes(10));
            when(usuarioRepository.findByCorreo("ana@foundia.dev")).thenReturn(Optional.of(u));

            AuthResponse respuesta = authService.verificar(new VerificarRequest("ANA@foundia.dev", "123456"));

            assertThat(respuesta.token()).isEqualTo("jwt-de-prueba");
            assertThat(u.isVerificado()).isTrue();
            assertThat(u.getCodigoVerificacion()).isNull();
        }

        @Test
        @DisplayName("Código incorrecto: 400 y la cuenta sigue sin verificar")
        void verificarCodigoIncorrecto() {
            Usuario u = usuario(false);
            u.setCodigoVerificacion("123456");
            u.setCodigoExpira(AHORA.plusMinutes(10));
            when(usuarioRepository.findByCorreo("ana@foundia.dev")).thenReturn(Optional.of(u));

            assertThatThrownBy(() -> authService.verificar(new VerificarRequest("ana@foundia.dev", "000000")))
                    .isInstanceOf(CodigoVerificacionException.class)
                    .hasMessageContaining("incorrecto");
            assertThat(u.isVerificado()).isFalse();
        }

        @Test
        @DisplayName("Código expirado: 400")
        void verificarCodigoExpirado() {
            Usuario u = usuario(false);
            u.setCodigoVerificacion("123456");
            u.setCodigoExpira(AHORA.minusMinutes(1));
            when(usuarioRepository.findByCorreo("ana@foundia.dev")).thenReturn(Optional.of(u));

            assertThatThrownBy(() -> authService.verificar(new VerificarRequest("ana@foundia.dev", "123456")))
                    .isInstanceOf(CodigoVerificacionException.class)
                    .hasMessageContaining("expiró");
        }

        @Test
        @DisplayName("Reenviar código: genera uno nuevo y lo envía")
        void reenviarCodigo() {
            Usuario u = usuario(false);
            u.setCodigoVerificacion("111111");
            u.setCodigoExpira(AHORA.minusMinutes(5));
            when(usuarioRepository.findByCorreo("ana@foundia.dev")).thenReturn(Optional.of(u));

            MensajeResponse respuesta = authService.reenviarCodigo(new ReenviarCodigoRequest("ana@foundia.dev"));

            assertThat(u.getCodigoExpira()).isAfter(AHORA);
            verify(emailService).enviarCodigoVerificacion(
                    eq("ana@foundia.dev"), eq("Ana"), eq(u.getCodigoVerificacion()), anyLong());
            assertThat(respuesta.mensaje()).isNotBlank();
        }
    }

    // ================================================================ H12
    @Nested
    @DisplayName("H12 - Inicio de sesión y bloqueo")
    class Login {

        @Test
        @DisplayName("Credenciales correctas: devuelve JWT y reinicia intentos")
        void loginCorrecto() {
            Usuario u = usuario(true);
            u.setIntentosFallidos(3);
            when(usuarioRepository.findByCorreo("ana@foundia.dev")).thenReturn(Optional.of(u));

            AuthResponse respuesta = authService.login(new LoginRequest("Ana@foundia.dev", PASSWORD));

            assertThat(respuesta.token()).isEqualTo("jwt-de-prueba");
            assertThat(respuesta.usuario().correo()).isEqualTo("ana@foundia.dev");
            assertThat(u.getIntentosFallidos()).isZero();
        }

        @Test
        @DisplayName("Correo inexistente: 401 con mensaje genérico")
        void correoInexistente() {
            when(usuarioRepository.findByCorreo(anyString())).thenReturn(Optional.empty());

            assertThatThrownBy(() -> authService.login(new LoginRequest("nadie@foundia.dev", PASSWORD)))
                    .isInstanceOf(InvalidCredentialsException.class)
                    .hasMessageContaining("incorrectos");
        }

        @Test
        @DisplayName("Contraseña incorrecta: 401, suma un intento e informa los restantes")
        void passwordIncorrecto() {
            Usuario u = usuario(true);
            when(usuarioRepository.findByCorreo("ana@foundia.dev")).thenReturn(Optional.of(u));

            assertThatThrownBy(() -> authService.login(new LoginRequest("ana@foundia.dev", "Mala123!")))
                    .isInstanceOf(InvalidCredentialsException.class)
                    .hasMessageContaining("4 intentos");
            assertThat(u.getIntentosFallidos()).isEqualTo(1);
            verify(usuarioRepository).save(u);
        }

        @Test
        @DisplayName("Quinto intento fallido: bloquea la cuenta 15 minutos (423)")
        void quintoIntentoBloquea() {
            Usuario u = usuario(true);
            u.setIntentosFallidos(4);
            when(usuarioRepository.findByCorreo("ana@foundia.dev")).thenReturn(Optional.of(u));

            assertThatThrownBy(() -> authService.login(new LoginRequest("ana@foundia.dev", "Mala123!")))
                    .isInstanceOf(CuentaBloqueadaException.class);
            assertThat(u.getBloqueadoHasta()).isEqualTo(AHORA.plusMinutes(AuthService.MINUTOS_BLOQUEO));
        }

        @Test
        @DisplayName("Cinco intentos seguidos desde cero terminan en bloqueo")
        void cincoIntentosSeguidos() {
            Usuario u = usuario(true);
            when(usuarioRepository.findByCorreo("ana@foundia.dev")).thenReturn(Optional.of(u));
            LoginRequest malo = new LoginRequest("ana@foundia.dev", "Mala123!");

            for (int i = 1; i < AuthService.MAX_INTENTOS; i++) {
                assertThatThrownBy(() -> authService.login(malo)).isInstanceOf(InvalidCredentialsException.class);
            }
            assertThatThrownBy(() -> authService.login(malo)).isInstanceOf(CuentaBloqueadaException.class);
        }

        @Test
        @DisplayName("Cuenta bloqueada: rechaza incluso con la contraseña correcta")
        void bloqueadaRechazaPasswordCorrecto() {
            Usuario u = usuario(true);
            u.setBloqueadoHasta(AHORA.plusMinutes(10));
            when(usuarioRepository.findByCorreo("ana@foundia.dev")).thenReturn(Optional.of(u));

            assertThatThrownBy(() -> authService.login(new LoginRequest("ana@foundia.dev", PASSWORD)))
                    .isInstanceOf(CuentaBloqueadaException.class)
                    .hasMessageContaining("10 minutos");
        }

        @Test
        @DisplayName("Bloqueo vencido: permite ingresar y limpia el bloqueo")
        void bloqueoVencido() {
            Usuario u = usuario(true);
            u.setBloqueadoHasta(AHORA.minusSeconds(1));
            when(usuarioRepository.findByCorreo("ana@foundia.dev")).thenReturn(Optional.of(u));

            AuthResponse respuesta = authService.login(new LoginRequest("ana@foundia.dev", PASSWORD));

            assertThat(respuesta.token()).isNotBlank();
            assertThat(u.getBloqueadoHasta()).isNull();
        }

        @Test
        @DisplayName("Cuenta sin verificar: 403")
        void cuentaNoVerificada() {
            when(usuarioRepository.findByCorreo("ana@foundia.dev")).thenReturn(Optional.of(usuario(false)));

            assertThatThrownBy(() -> authService.login(new LoginRequest("ana@foundia.dev", PASSWORD)))
                    .isInstanceOf(CuentaNoVerificadaException.class);
        }

        @Test
        @DisplayName("Cuenta suspendida: 401 con mensaje de suspensión")
        void cuentaSuspendida() {
            Usuario u = usuario(true);
            u.setEstado(EstadoUsuario.SUSPENDIDO);
            when(usuarioRepository.findByCorreo("ana@foundia.dev")).thenReturn(Optional.of(u));

            assertThatThrownBy(() -> authService.login(new LoginRequest("ana@foundia.dev", PASSWORD)))
                    .isInstanceOf(InvalidCredentialsException.class)
                    .hasMessageContaining("suspendida");
        }
    }
}
