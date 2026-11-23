package com.proyectointegrador.controller;

import com.proyectointegrador.dto.VisitaCampanaRequest;
import com.proyectointegrador.service.AlcanceCampanaService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/** H67: la web avisa que alguien llegó por el enlace de una campaña. Sin sesión. */
@RestController
@RequestMapping("/api/campanas")
public class AlcanceCampanaController {

    private final AlcanceCampanaService alcance;

    public AlcanceCampanaController(AlcanceCampanaService alcance) {
        this.alcance = alcance;
    }

    /** 204 aunque el origen no exista o la visita ya se haya contado: no revela qué orígenes existen. */
    @PostMapping("/visitas")
    public ResponseEntity<?> visita(@Valid @RequestBody VisitaCampanaRequest visita, HttpServletRequest request) {
        if (!alcance.dentroDelLimite(request.getRemoteAddr())) {
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .header("Retry-After", "60")
                    .body(Map.of("status", 429, "mensaje", "Demasiadas visitas seguidas. Intenta en un minuto."));
        }
        alcance.registrarVisita(visita);
        return ResponseEntity.noContent().build();
    }
}
