package com.proyectointegrador.controller;

import com.proyectointegrador.dto.DispositivoRequest;
import com.proyectointegrador.dto.DispositivoResponse;
import com.proyectointegrador.exception.InvalidCredentialsException;
import com.proyectointegrador.security.JwtService;
import com.proyectointegrador.security.UserPrincipal;
import com.proyectointegrador.service.DispositivoService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** H57: registrar, listar y dar de baja los teléfonos que reciben push. */
@RestController
@RequestMapping("/api/dispositivos")
public class DispositivoController {

    private final DispositivoService dispositivos;
    private final JwtService jwt;

    public DispositivoController(DispositivoService dispositivos, JwtService jwt) {
        this.dispositivos = dispositivos;
        this.jwt = jwt;
    }

    @PostMapping
    public ResponseEntity<DispositivoResponse> registrar(
            @Valid @RequestBody DispositivoRequest datos,
            @RequestHeader(value = "Authorization", required = false) String authorization) {
        var registro = dispositivos.registrar(usuarioActual().getId(), datos, jtiDe(authorization));
        return ResponseEntity.status(registro.nuevo() ? HttpStatus.CREATED : HttpStatus.OK).body(registro.dispositivo());
    }

    @GetMapping
    public List<DispositivoResponse> listar() {
        return dispositivos.listar(usuarioActual().getId());
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        dispositivos.eliminar(usuarioActual().getId(), id);
        return ResponseEntity.noContent().build();
    }

    private String jtiDe(String authorization) {
        if (authorization == null || !authorization.startsWith("Bearer ")) {
            return null;
        }
        try {
            return jwt.extractJti(authorization.substring(7).trim());
        } catch (RuntimeException ex) {
            return null;
        }
    }

    private UserPrincipal usuarioActual() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            throw new InvalidCredentialsException("No autenticado");
        }
        return principal;
    }
}
