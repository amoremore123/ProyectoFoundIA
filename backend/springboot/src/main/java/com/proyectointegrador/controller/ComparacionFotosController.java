package com.proyectointegrador.controller;

import com.proyectointegrador.exception.InvalidCredentialsException;
import com.proyectointegrador.security.UserPrincipal;
import com.proyectointegrador.service.ComparacionFotosService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * POST /api/objetos/{id}/coincidencias/comparar-fotos: la IA compara la foto de mi publicación con
 * las de sus coincidencias sugeridas. Solo quien publicó; se pide a mano porque usa el cupo de la IA.
 */
@RestController
public class ComparacionFotosController {

    private final ComparacionFotosService service;

    public ComparacionFotosController(ComparacionFotosService service) {
        this.service = service;
    }

    @PostMapping("/api/objetos/{id}/coincidencias/comparar-fotos")
    public ComparacionFotosService.ComparacionFotos comparar(@PathVariable Long id) {
        Authentication autenticacion = SecurityContextHolder.getContext().getAuthentication();
        if (autenticacion == null || !(autenticacion.getPrincipal() instanceof UserPrincipal principal)) {
            throw new InvalidCredentialsException("No autenticado");
        }
        return service.comparar(id, principal.getId());
    }
}
