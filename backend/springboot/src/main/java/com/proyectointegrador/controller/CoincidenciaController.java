package com.proyectointegrador.controller;

import com.proyectointegrador.dto.CoincidenciaDetalleResponse;
import com.proyectointegrador.service.CoincidenciaService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/coincidencias")
public class CoincidenciaController {
    private final CoincidenciaService coincidenciaService;

    public CoincidenciaController(CoincidenciaService coincidenciaService) {
        this.coincidenciaService = coincidenciaService;
    }

    @GetMapping("/{id}")
    public CoincidenciaDetalleResponse detalle(@PathVariable String id) {
        return coincidenciaService.detalle(id);
    }
}
