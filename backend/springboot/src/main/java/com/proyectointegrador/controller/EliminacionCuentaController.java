package com.proyectointegrador.controller;

import com.proyectointegrador.security.UserPrincipal;
import com.proyectointegrador.service.EliminacionCuentaService;
import com.proyectointegrador.exception.SolicitudInvalidaException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.Map;

@RestController
@RequestMapping("/api/perfil/solicitud-eliminacion")
public class EliminacionCuentaController {
    private final EliminacionCuentaService servicio;
    public EliminacionCuentaController(EliminacionCuentaService servicio) { this.servicio = servicio; }
    @GetMapping
    public EliminacionCuentaService.Estado consultar(@AuthenticationPrincipal UserPrincipal usuario) {
        return servicio.consultar(usuario.getId());
    }
    @PostMapping
    public ResponseEntity<EliminacionCuentaService.Estado> solicitar(@AuthenticationPrincipal UserPrincipal usuario,
                                                                   @RequestBody Map<String,Object> body) {
        if (body.size() != 1 || !Boolean.TRUE.equals(body.get("confirmar")))
            throw new SolicitudInvalidaException("Confirma la solicitud con confirmar: true. No envíes datos de otra cuenta.");
        var respuesta = servicio.solicitar(usuario.getId());
        return ResponseEntity.status(respuesta.repetido() ? 200 : 201).body(respuesta);
    }
}
