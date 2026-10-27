package com.proyectointegrador.controller;

import com.proyectointegrador.dto.PreferenciasRequest;
import com.proyectointegrador.exception.InvalidCredentialsException;
import com.proyectointegrador.security.UserPrincipal;
import com.proyectointegrador.service.PreferenciaNotificacionService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController @RequestMapping("/api/notificaciones/preferencias")
public class PreferenciasController {
    private final PreferenciaNotificacionService preferencias;
    public PreferenciasController(PreferenciaNotificacionService preferencias) {this.preferencias=preferencias;}
    @GetMapping public PreferenciasRequest obtener(@AuthenticationPrincipal UserPrincipal usuario) {
        return preferencias.obtener(id(usuario));
    }
    @PutMapping public PreferenciasRequest guardar(@AuthenticationPrincipal UserPrincipal usuario, @Valid @RequestBody PreferenciasRequest datos) {
        return preferencias.guardar(id(usuario),datos);
    }
    private Long id(UserPrincipal usuario) {
        if(usuario==null) throw new InvalidCredentialsException("No autenticado");
        return usuario.getId();
    }
}
