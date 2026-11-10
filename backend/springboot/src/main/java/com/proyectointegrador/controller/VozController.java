package com.proyectointegrador.controller;

import com.proyectointegrador.dto.VozRequest;
import com.proyectointegrador.security.UserPrincipal;
import com.proyectointegrador.service.VozService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;
import java.time.Duration;

/** Extensión de voz de FINDI, separada del contrato compartido de H45. */
@RestController
public class VozController {
    private final VozService vozService;

    public VozController(VozService vozService) {
        this.vozService = vozService;
    }

    @PostMapping("/api/asistente/voz")
    public ResponseEntity<byte[]> voz(@Valid @RequestBody VozRequest solicitud,
                                     @AuthenticationPrincipal UserPrincipal usuario,
                                     HttpServletRequest request) {
        String clave = usuario != null && usuario.getId() != null
                ? "usuario:" + usuario.getId() : "ip:" + request.getRemoteAddr();
        byte[] audio = vozService.sintetizar(solicitud.texto(), clave);
        return ResponseEntity.ok().contentType(MediaType.valueOf("audio/mpeg"))
                .cacheControl(CacheControl.maxAge(Duration.ofHours(1)).cachePrivate())
                .body(audio);
    }
}
