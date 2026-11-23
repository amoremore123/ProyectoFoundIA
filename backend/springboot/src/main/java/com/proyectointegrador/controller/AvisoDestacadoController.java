package com.proyectointegrador.controller;

import com.proyectointegrador.dto.AvisoDestacadoResponse;
import com.proyectointegrador.service.AvisoDestacadoService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** H71: avisos destacados vigentes, sin sesión, para mostrarlos arriba en la web y en la app. */
@RestController
@RequestMapping("/api/avisos-destacados")
public class AvisoDestacadoController {

    private final AvisoDestacadoService avisos;

    public AvisoDestacadoController(AvisoDestacadoService avisos) {
        this.avisos = avisos;
    }

    @GetMapping
    public List<AvisoDestacadoResponse> vigentes() {
        return avisos.vigentes();
    }
}
