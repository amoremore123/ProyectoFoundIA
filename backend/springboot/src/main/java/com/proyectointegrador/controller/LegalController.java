package com.proyectointegrador.controller;

import com.proyectointegrador.service.DocumentosLegales;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/legal")
public class LegalController {
    @GetMapping("/documentos")
    public DocumentosLegales.Documentos documentos() { return DocumentosLegales.documentos(); }
}
