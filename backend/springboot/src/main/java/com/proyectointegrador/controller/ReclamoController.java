package com.proyectointegrador.controller;

import com.proyectointegrador.dto.ReclamoResponse;
import com.proyectointegrador.exception.InvalidCredentialsException;
import com.proyectointegrador.security.UserPrincipal;
import com.proyectointegrador.service.ReclamoService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/objetos/{objetoId}/reclamos")
public class ReclamoController {

    private final ReclamoService reclamoService;

    public ReclamoController(ReclamoService reclamoService) {
        this.reclamoService = reclamoService;
    }

    @PostMapping
    public ResponseEntity<ReclamoResponse> reclamar(@PathVariable Long objetoId) {
        ReclamoResponse reclamo = reclamoService.reclamar(objetoId, usuarioActual().getId());
        return ResponseEntity.status(reclamo.repetido() ? HttpStatus.OK : HttpStatus.CREATED).body(reclamo);
    }

    @GetMapping("/custodia")
    public ReclamoResponse miReclamoCustodia(@PathVariable Long objetoId) {
        return reclamoService.miReclamoCustodia(objetoId, usuarioActual().getId());
    }

    @GetMapping("/custodia/qr-retiro")
    public ResponseEntity<ReclamoService.QrPropio> qrPropio(@PathVariable Long objetoId) {
        return ResponseEntity.ok().cacheControl(org.springframework.http.CacheControl.noStore())
                .body(reclamoService.qrPropio(objetoId, usuarioActual().getId()));
    }

    @PostMapping("/custodia")
    public ResponseEntity<ReclamoResponse> reclamarCustodia(@PathVariable Long objetoId,
            @Valid @RequestBody RespuestaCustodia datos) {
        ReclamoResponse reclamo = reclamoService.reclamarCustodia(objetoId, usuarioActual().getId(), datos.respuesta());
        return ResponseEntity.status(reclamo.repetido() ? HttpStatus.OK : HttpStatus.CREATED).body(reclamo);
    }

    public record RespuestaCustodia(@NotBlank @Size(max = 500) String respuesta) {}

    private UserPrincipal usuarioActual() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            throw new InvalidCredentialsException("No autenticado");
        }
        return principal;
    }
}
