package com.proyectointegrador.controller;

import com.proyectointegrador.dto.ObjetoResponse;
import com.proyectointegrador.dto.PerfilUpdateRequest;
import com.proyectointegrador.dto.UsuarioResponse;
import com.proyectointegrador.exception.InvalidCredentialsException;
import com.proyectointegrador.security.UserPrincipal;
import com.proyectointegrador.service.PerfilService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/perfil")
public class PerfilController {

    private final PerfilService perfilService;

    private final com.proyectointegrador.service.InvitacionService invitaciones;

    private final com.proyectointegrador.service.FotoPerfilService fotoPerfil;

    public PerfilController(PerfilService perfilService, com.proyectointegrador.service.InvitacionService invitaciones,
                            com.proyectointegrador.service.FotoPerfilService fotoPerfil) {
        this.perfilService = perfilService;
        this.invitaciones = invitaciones;
        this.fotoPerfil = fotoPerfil;
    }

    /** H64: sube o reemplaza la foto de perfil (parte multipart «archivo», JPEG o PNG hasta 5 MiB). */
    @org.springframework.web.bind.annotation.PostMapping(value = "/foto",
            consumes = org.springframework.http.MediaType.MULTIPART_FORM_DATA_VALUE)
    public java.util.Map<String, String> subirFoto(
            @org.springframework.web.bind.annotation.RequestParam(value = "archivo", required = false)
            org.springframework.web.multipart.MultipartFile archivo) {
        return java.util.Map.of("fotoPerfilUrl", fotoPerfil.subir(usuarioActual().getId(), archivo));
    }

    /** H64: la foto solo se entrega a la propia cuenta, con caché privada. */
    @GetMapping("/foto")
    public ResponseEntity<org.springframework.core.io.Resource> verFoto() {
        return fotoPerfil.archivo(usuarioActual().getId())
                .map(archivo -> ResponseEntity.ok()
                        .contentType(archivo.toString().endsWith(".png")
                                ? org.springframework.http.MediaType.IMAGE_PNG
                                : org.springframework.http.MediaType.IMAGE_JPEG)
                        .cacheControl(org.springframework.http.CacheControl
                                .maxAge(java.time.Duration.ofHours(1)).cachePrivate())
                        .<org.springframework.core.io.Resource>body(
                                new org.springframework.core.io.FileSystemResource(archivo)))
                .orElseThrow(() -> new com.proyectointegrador.exception.ResourceNotFoundException(
                        "La cuenta no tiene foto de perfil"));
    }

    /** H64: quita la foto; repetirlo es seguro. */
    @org.springframework.web.bind.annotation.DeleteMapping("/foto")
    public ResponseEntity<Void> eliminarFoto() {
        fotoPerfil.eliminar(usuarioActual().getId());
        return ResponseEntity.noContent().build();
    }

    @GetMapping
    public ResponseEntity<UsuarioResponse> obtener() {
        return ResponseEntity.ok(perfilService.obtener(usuarioActual().getId()));
    }

    @PutMapping
    public ResponseEntity<UsuarioResponse> actualizar(@Valid @RequestBody PerfilUpdateRequest request) {
        return ResponseEntity.ok(perfilService.actualizar(usuarioActual().getId(), request));
    }

    @org.springframework.web.bind.annotation.PostMapping("/invitaciones")
    public java.util.Map<String, String> crearInvitacion() {
        return java.util.Map.of("url", invitaciones.crear(usuarioActual().getId()));
    }

    @GetMapping("/objetos")
    public ResponseEntity<List<ObjetoResponse>> objetos() {
        return ResponseEntity.ok(perfilService.objetos(usuarioActual().getId()));
    }

    private UserPrincipal usuarioActual() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            throw new InvalidCredentialsException("No autenticado");
        }
        return principal;
    }
}
