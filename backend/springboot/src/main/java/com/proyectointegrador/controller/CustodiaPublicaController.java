package com.proyectointegrador.controller;

import com.proyectointegrador.service.CustodiaPublicaService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** H41: GET /api/custodias/verificar?t=... — público, lo usa la página que abre el QR de la etiqueta. */
@RestController
@RequestMapping("/api/custodias")
public class CustodiaPublicaController {

    private final CustodiaPublicaService service;

    public CustodiaPublicaController(CustodiaPublicaService service) {
        this.service = service;
    }

    @GetMapping("/verificar")
    public CustodiaPublicaService.CustodiaPublica verificar(@RequestParam(name = "t", defaultValue = "") String t) {
        return service.verificar(t);
    }
}
