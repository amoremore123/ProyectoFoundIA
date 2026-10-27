package com.proyectointegrador.controller;

import com.proyectointegrador.dto.ReporteRequest;
import com.proyectointegrador.dto.ReporteResponse;
import com.proyectointegrador.exception.InvalidCredentialsException;
import com.proyectointegrador.security.UserPrincipal;
import com.proyectointegrador.service.ReporteService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/objetos/{objetoId}/reportes")
public class ReporteController {
    private final ReporteService reportes;

    public ReporteController(ReporteService reportes) {
        this.reportes = reportes;
    }

    @PostMapping
    public ResponseEntity<ReporteResponse> reportar(@PathVariable Long objetoId,
            @AuthenticationPrincipal UserPrincipal usuario, @Valid @RequestBody ReporteRequest datos) {
        if (usuario == null) throw new InvalidCredentialsException("No autenticado");
        var respuesta = reportes.reportar(objetoId, usuario.getId(), datos);
        return ResponseEntity.status(respuesta.repetido() ? HttpStatus.OK : HttpStatus.CREATED).body(respuesta);
    }
}
