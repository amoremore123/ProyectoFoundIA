package com.proyectointegrador.controller;

import com.proyectointegrador.dto.ChatDtos.ConversacionResponse;
import com.proyectointegrador.exception.InvalidCredentialsException;
import com.proyectointegrador.security.UserPrincipal;
import com.proyectointegrador.service.EtiquetaService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * Etiqueta QR preventiva.
 * GET/POST /api/etiquetas y DELETE /api/etiquetas/{id}: mis pertenencias (con sesión).
 * GET /api/etiquetas/publica/{codigo}: lo que ve quien escanea (público, sin datos del dueño).
 * POST /api/etiquetas/publica/{codigo}/avisar: escribir al dueño por el chat (con sesión).
 */
@RestController
@RequestMapping("/api/etiquetas")
public class EtiquetaController {

    public record NuevaPertenencia(String nombre, String descripcion) {}

    public record Aviso(String mensaje) {}

    private final EtiquetaService service;

    public EtiquetaController(EtiquetaService service) {
        this.service = service;
    }

    @GetMapping
    public List<EtiquetaService.PertenenciaResponse> mias() {
        return service.mias(usuario().getId());
    }

    @PostMapping
    public ResponseEntity<EtiquetaService.PertenenciaResponse> crear(@RequestBody NuevaPertenencia req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.crear(usuario().getId(), req.nombre(), req.descripcion()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> desactivar(@PathVariable Long id) {
        service.desactivar(id, usuario().getId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/publica/{codigo}")
    public EtiquetaService.EtiquetaPublica publica(@PathVariable String codigo) {
        UserPrincipal yo = usuarioOpcional();
        return service.publica(codigo, yo == null ? null : yo.getId());
    }

    @PostMapping("/publica/{codigo}/avisar")
    public ResponseEntity<ConversacionResponse> avisar(@PathVariable String codigo, @RequestBody Aviso req) {
        return ResponseEntity.status(HttpStatus.CREATED).body(service.avisar(codigo, usuario().getId(), req.mensaje()));
    }

    private static UserPrincipal usuarioOpcional() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        return a != null && a.getPrincipal() instanceof UserPrincipal u ? u : null;
    }

    private static UserPrincipal usuario() {
        UserPrincipal u = usuarioOpcional();
        if (u == null) throw new InvalidCredentialsException("No autenticado");
        return u;
    }
}
