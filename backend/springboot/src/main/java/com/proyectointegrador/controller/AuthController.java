package com.proyectointegrador.controller;

import com.proyectointegrador.dto.AuthResponse;
import com.proyectointegrador.dto.LoginRequest;
import com.proyectointegrador.dto.MensajeResponse;
import com.proyectointegrador.dto.ReenviarCodigoRequest;
import com.proyectointegrador.dto.RegisterRequest;
import com.proyectointegrador.dto.RegistroResponse;
import com.proyectointegrador.dto.VerificarRequest;
import com.proyectointegrador.service.AuthService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private final AuthService authService;

    public AuthController(AuthService authService) {
        this.authService = authService;
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

    /** H12 - Login con JWT; bloquea la cuenta 15 min tras 5 intentos fallidos. */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }
}
