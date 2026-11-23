package com.proyectointegrador.controller;

import com.proyectointegrador.exception.InvalidCredentialsException;
import com.proyectointegrador.security.UserPrincipal;
import com.proyectointegrador.service.ReconocimientoService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** GET /api/perfil/reconocimiento: objetos que entregué en la biblioteca y mi insignia. Solo la cuenta propia. */
@RestController
public class ReconocimientoController {

    private final ReconocimientoService service;

    public ReconocimientoController(ReconocimientoService service) {
        this.service = service;
    }

    @GetMapping("/api/perfil/reconocimiento")
    public ReconocimientoService.Reconocimiento reconocimiento() {
        Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacion == null || !(autenticacion.getPrincipal() instanceof UserPrincipal principal)) {
            throw new InvalidCredentialsException("No autenticado");
        }
        return service.de(principal.getId());
    }
}
