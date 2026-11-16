package com.proyectointegrador.controller;

import com.proyectointegrador.dto.ObjetoResponse;
import com.proyectointegrador.dto.PerfilUpdateRequest;
import com.proyectointegrador.dto.UsuarioResponse;
import com.proyectointegrador.exception.InvalidCredentialsException;
import com.proyectointegrador.security.UserPrincipal;
import com.proyectointegrador.service.PerfilService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/perfil")
public class PerfilController {

    private final PerfilService perfilService;

    private final com.proyectointegrador.service.InvitacionService invitaciones;

    public PerfilController(PerfilService perfilService, com.proyectointegrador.service.InvitacionService invitaciones) {
        this.perfilService = perfilService;
        this.invitaciones = invitaciones;
    }

    @GetMapping
    public ResponseEntity<UsuarioResponse> obtener() {
        return ResponseEntity.ok(perfilService.obtener(usuarioActual().getId()));
    }

    @PutMapping
    public ResponseEntity<UsuarioResponse> actualizar(@Valid @RequestBody PerfilUpdateRequest request) {
        return ResponseEntity.ok(perfilService.actualizar(usuarioActual().getId(), request));
    }

    @org.springframework.web.bind.annotation.PostMapping("/invitaciones")
    public java.util.Map<String, String> crearInvitacion() {
        return java.util.Map.of("url", invitaciones.crear(usuarioActual().getId()));
    }

    @GetMapping("/objetos")
    public ResponseEntity<List<ObjetoResponse>> objetos() {
        return ResponseEntity.ok(perfilService.objetos(usuarioActual().getId()));
    }

    private UserPrincipal usuarioActual() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            throw new InvalidCredentialsException("No autenticado");
        }
        return principal;
    }
}
