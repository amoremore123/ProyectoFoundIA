package com.proyectointegrador.controller;

import com.proyectointegrador.dto.CoincidenciaDetalleResponse;
import com.proyectointegrador.dto.CoincidenciaEstadoResponse;
import com.proyectointegrador.exception.InvalidCredentialsException;
import com.proyectointegrador.security.UserPrincipal;
import com.proyectointegrador.service.CoincidenciaService;
import com.proyectointegrador.service.GestionCoincidenciaService;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/coincidencias")
public class CoincidenciaController {
    private final CoincidenciaService coincidenciaService;
    private final GestionCoincidenciaService gestion;

    public CoincidenciaController(CoincidenciaService coincidenciaService, GestionCoincidenciaService gestion) {
        this.coincidenciaService = coincidenciaService;
        this.gestion = gestion;
    }

    @GetMapping("/{id}")
    public CoincidenciaDetalleResponse detalle(@PathVariable String id) {
        return coincidenciaService.detalle(id);
    }

    @PostMapping("/{id}/descartar")
    public CoincidenciaEstadoResponse descartar(@PathVariable String id) {
        return gestion.descartar(id, usuarioActual().getId());
    }

    private UserPrincipal usuarioActual() {
        var authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal usuario)) {
            throw new InvalidCredentialsException("No autenticado");
        }
        return usuario;
    }
}
