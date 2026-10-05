package com.proyectointegrador.controller;

import com.proyectointegrador.dto.AuthResponse;
import com.proyectointegrador.dto.LoginRequest;
import com.proyectointegrador.dto.RegisterRequest;
import com.proyectointegrador.dto.RegistroResponse;
import com.proyectointegrador.dto.UsuarioResponse;
import com.proyectointegrador.dto.VerificarRequest;
import com.proyectointegrador.exception.CuentaBloqueadaException;
import com.proyectointegrador.exception.CuentaNoVerificadaException;
import com.proyectointegrador.exception.DuplicateResourceException;
import com.proyectointegrador.exception.GlobalExceptionHandler;
import com.proyectointegrador.exception.InvalidCredentialsException;
import com.proyectointegrador.service.AuthService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import static org.hamcrest.Matchers.containsString;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@DisplayName("H11/H12 - Endpoints /api/auth")
class AuthControllerTest {

    private AuthService authService;
    private MockMvc mockMvc;

    private static final String REGISTRO_VALIDO = """
            {"nombre":"Ana","apellido":"Pérez","correo":"ana@foundia.dev","password":"Segura123!"}""";
    private static final String LOGIN = """
            {"correo":"ana@foundia.dev","password":"Segura123!"}""";

    @BeforeEach
    void setUp() {
        authService = mock(AuthService.class);
        LocalValidatorFactoryBean validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();
        mockMvc = MockMvcBuilders.standaloneSetup(new AuthController(authService))
                .setControllerAdvice(new GlobalExceptionHandler())
                .setValidator(validator)
                .build();
    }

    private AuthResponse respuestaConToken() {
        return new AuthResponse("jwt-de-prueba",
                new UsuarioResponse(1L, "Ana", "Pérez", "ana@foundia.dev", "USUARIO", "ACTIVO", null));
    }

    @Test
    @DisplayName("POST /register válido -> 201")
    void registroValido() throws Exception {
        when(authService.register(any(RegisterRequest.class)))
                .thenReturn(new RegistroResponse("Te enviamos un código", "ana@foundia.dev"));

        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(REGISTRO_VALIDO))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.correo").value("ana@foundia.dev"));
    }

    @Test
    @DisplayName("POST /register con contraseña débil -> 400 y no llama al servicio")
    void registroPasswordDebil() throws Exception {
        String body = """
                {"nombre":"Ana","apellido":"Pérez","correo":"ana@foundia.dev","password":"123456"}""";

        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.mensaje", containsString("password")));
        verify(authService, never()).register(any());
    }

    @Test
    @DisplayName("POST /register correo duplicado -> 409")
    void registroDuplicado() throws Exception {
        when(authService.register(any())).thenThrow(new DuplicateResourceException("El correo ya está registrado"));

        mockMvc.perform(post("/api/auth/register").contentType(MediaType.APPLICATION_JSON).content(REGISTRO_VALIDO))
                .andExpect(status().isConflict());
    }

    @Test
    @DisplayName("POST /verificar código correcto -> 200 con token")
    void verificar() throws Exception {
        when(authService.verificar(any(VerificarRequest.class))).thenReturn(respuestaConToken());

        mockMvc.perform(post("/api/auth/verificar").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\":\"ana@foundia.dev\",\"codigo\":\"123456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt-de-prueba"));
    }

    @Test
    @DisplayName("POST /verificar código con letras -> 400")
    void verificarCodigoInvalido() throws Exception {
        mockMvc.perform(post("/api/auth/verificar").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"correo\":\"ana@foundia.dev\",\"codigo\":\"12ab\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @DisplayName("POST /login correcto -> 200 con token JWT")
    void loginCorrecto() throws Exception {
        when(authService.login(any(LoginRequest.class))).thenReturn(respuestaConToken());

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(LOGIN))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.token").value("jwt-de-prueba"))
                .andExpect(jsonPath("$.usuario.correo").value("ana@foundia.dev"));
    }

    @Test
    @DisplayName("POST /login credenciales incorrectas -> 401 con mensaje")
    void loginIncorrecto() throws Exception {
        when(authService.login(any())).thenThrow(
                new InvalidCredentialsException("Correo o contraseña incorrectos. Te quedan 4 intentos."));

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(LOGIN))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.mensaje", containsString("4 intentos")));
    }

    @Test
    @DisplayName("POST /login cuenta bloqueada -> 423")
    void loginBloqueado() throws Exception {
        when(authService.login(any())).thenThrow(new CuentaBloqueadaException("Tu cuenta está bloqueada"));

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(LOGIN))
                .andExpect(status().isLocked());
    }

    @Test
    @DisplayName("POST /login cuenta sin verificar -> 403")
    void loginNoVerificado() throws Exception {
        when(authService.login(any())).thenThrow(new CuentaNoVerificadaException("Debes verificar tu correo"));

        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON).content(LOGIN))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /login sin correo -> 400")
    void loginSinCorreo() throws Exception {
        mockMvc.perform(post("/api/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"password\":\"x\"}"))
                .andExpect(status().isBadRequest());
    }
}
