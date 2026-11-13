package com.proyectointegrador.controller;

import com.proyectointegrador.dto.HistorialNotificacionesResponse;
import com.proyectointegrador.dto.MarcarTodasResponse;
import com.proyectointegrador.dto.NotificacionResponse;
import com.proyectointegrador.exception.InvalidCredentialsException;
import com.proyectointegrador.security.UserPrincipal;
import com.proyectointegrador.service.NotificacionService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/notificaciones")
public class NotificacionController {

    private final NotificacionService notificacionService;

    public NotificacionController(NotificacionService notificacionService) {
        this.notificacionService = notificacionService;
    }

    @GetMapping
    public ResponseEntity<List<NotificacionResponse>> listar() {
        return ResponseEntity.ok(notificacionService.listar(usuarioActual().getId()));
    }

    // H25: historial paginado con filtro; GET /api/notificaciones sigue devolviendo la lista completa
    @GetMapping("/historial")
    public ResponseEntity<HistorialNotificacionesResponse> historial(
            @RequestParam(defaultValue = "TODAS") String estado,
            @RequestParam(defaultValue = "0") int pagina,
            @RequestParam(defaultValue = "20") int tamano) {
        return ResponseEntity.ok(notificacionService.historial(usuarioActual().getId(), estado, pagina, tamano));
    }

    // H25: marcar todas como leídas en la cuenta propia (no recibe id de usuario)
    @PutMapping("/leidas")
    public ResponseEntity<MarcarTodasResponse> marcarTodasLeidas() {
        return ResponseEntity.ok(notificacionService.marcarTodasLeidas(usuarioActual().getId()));
    }

    @PutMapping("/{id}/leida")
    public ResponseEntity<NotificacionResponse> marcarLeida(@PathVariable Long id) {
        return ResponseEntity.ok(notificacionService.marcarLeida(id, usuarioActual().getId()));
    }

    private UserPrincipal usuarioActual() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            throw new InvalidCredentialsException("No autenticado");
        }
        return principal;
    }
}
