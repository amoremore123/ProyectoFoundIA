package com.proyectointegrador.controller;

import com.proyectointegrador.dto.AsistenteRequest;
import com.proyectointegrador.dto.AsistenteResponse;
import com.proyectointegrador.security.AsistenteRateLimiter;
import com.proyectointegrador.security.UserPrincipal;
import com.proyectointegrador.service.AsistenteService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * H45: POST /api/asistente/interpretar
 *
 * Es público para que el asistente funcione sin iniciar sesión. Si llega un
 * token válido, el JwtAuthenticationFilter deja la sesión en el contexto y la
 * respuesta añade el resumen propio (solo cifras).
 */
@RestController
@RequestMapping("/api/asistente")
public class AsistenteController {

    private final AsistenteService asistenteService;
    private final AsistenteRateLimiter rateLimiter;

    public AsistenteController(AsistenteService asistenteService, AsistenteRateLimiter rateLimiter) {
        this.asistenteService = asistenteService;
        this.rateLimiter = rateLimiter;
    }

    @PostMapping("/interpretar")
    public ResponseEntity<AsistenteResponse> interpretar(
            @Valid @RequestBody AsistenteRequest solicitud,
            HttpServletRequest request) {
        Long usuarioId = usuarioActualId();
        String clave = usuarioId == null
                ? "ip:" + request.getRemoteAddr()
                : "usuario:" + usuarioId;
        rateLimiter.verificar(clave);
        return ResponseEntity.ok(asistenteService.interpretar(solicitud.mensaje(), usuarioId));
    }

    private Long usuarioActualId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal principal) {
            return principal.getId();
        }
        return null;
    }
}
