package com.proyectointegrador.controller;

import com.proyectointegrador.dto.AuthResponse;
import com.proyectointegrador.dto.LoginRequest;
import com.proyectointegrador.dto.MensajeResponse;
import com.proyectointegrador.dto.RecuperarPasswordRequest;
import com.proyectointegrador.dto.ReenviarCodigoRequest;
import com.proyectointegrador.dto.RegisterRequest;
import com.proyectointegrador.dto.RegistroResponse;
import com.proyectointegrador.dto.RestablecerPasswordRequest;
import com.proyectointegrador.dto.VerificarRequest;
import com.proyectointegrador.security.UserPrincipal;
import com.proyectointegrador.service.AuthService;
import com.proyectointegrador.service.CierreSesionService;
import com.proyectointegrador.service.RecuperacionPasswordService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;
    private final RecuperacionPasswordService recuperacionPasswordService;
    private final CierreSesionService cierreSesionService;

    public AuthController(AuthService authService, RecuperacionPasswordService recuperacionPasswordService,
                          CierreSesionService cierreSesionService) {
        this.authService = authService;
        this.recuperacionPasswordService = recuperacionPasswordService;
        this.cierreSesionService = cierreSesionService;
    }

    /** H11 - Crea la cuenta (sin verificar) y envía el código al correo. */
    @PostMapping("/register")
    public ResponseEntity<RegistroResponse> register(@Valid @RequestBody RegisterRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(request));
    }

    /** H11 - Confirma el correo con el código de 6 dígitos y devuelve el JWT. */
    @PostMapping("/verificar")
    public ResponseEntity<AuthResponse> verificar(@Valid @RequestBody VerificarRequest request) {
        return ResponseEntity.ok(authService.verificar(request));
    }

    /** H11 - Genera y envía un código nuevo. */
    @PostMapping("/reenviar-codigo")
    public ResponseEntity<MensajeResponse> reenviarCodigo(@Valid @RequestBody ReenviarCodigoRequest request) {
        return ResponseEntity.ok(authService.reenviarCodigo(request));
    }

    /** H35 - Envía un enlace de un solo uso; la respuesta es igual exista o no la cuenta. */
    @PostMapping("/recuperar-password")
    public ResponseEntity<MensajeResponse> recuperarPassword(@Valid @RequestBody RecuperarPasswordRequest request) {
        return ResponseEntity.ok(recuperacionPasswordService.solicitar(request));
    }

    /** H35 - Define la contraseña nueva con el token recibido e invalida el enlace. */
    @PostMapping("/restablecer-password")
    public ResponseEntity<MensajeResponse> restablecerPassword(@Valid @RequestBody RestablecerPasswordRequest request) {
        return ResponseEntity.ok(recuperacionPasswordService.restablecer(request));
    }

    /** H12 - Login con JWT; bloquea la cuenta 15 min tras 5 intentos fallidos. */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }

    /** H37 - Revoca únicamente el token con el que se llama. */
    @PostMapping("/logout")
    public ResponseEntity<MensajeResponse> logout(Authentication authentication,
                                                  @RequestHeader("Authorization") String authorization) {
        boolean todas = cierreSesionService.cerrarSesion(
                principal(authentication).getId(), tokenDe(authorization));
        return ResponseEntity.ok(new MensajeResponse(todas
                ? "Sesión cerrada en todos los dispositivos"
                : "Sesión cerrada"));
    }

    /** H37 - Invalida todos los tokens de la cuenta, incluidos otros dispositivos. */
    @PostMapping("/logout-all")
    public ResponseEntity<MensajeResponse> logoutAll(Authentication authentication) {
        cierreSesionService.cerrarTodasLasSesiones(principal(authentication).getId());
        return ResponseEntity.ok(new MensajeResponse("Sesiones cerradas en todos los dispositivos"));
    }

    private UserPrincipal principal(Authentication authentication) {
        return (UserPrincipal) authentication.getPrincipal();
    }

    private String tokenDe(String authorization) {
        return authorization != null && authorization.startsWith("Bearer ")
                ? authorization.substring(7).trim()
                : "";
    }
}
