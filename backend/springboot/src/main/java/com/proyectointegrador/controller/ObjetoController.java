package com.proyectointegrador.controller;

import com.proyectointegrador.dto.CoincidenciaSugeridaResponse;
import com.proyectointegrador.dto.ObjetoRequest;
import com.proyectointegrador.dto.ObjetoResponse;
import com.proyectointegrador.entity.EstadoObjeto;
import com.proyectointegrador.entity.TipoObjeto;
import com.proyectointegrador.exception.InvalidCredentialsException;
import com.proyectointegrador.security.UserPrincipal;
import com.proyectointegrador.service.ObjetoService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/objetos")
public class ObjetoController {

    private final ObjetoService objetoService;

    public ObjetoController(ObjetoService objetoService) {
        this.objetoService = objetoService;
    }

    @GetMapping("/buscar")
    public ResponseEntity<List<ObjetoResponse>> buscar(
            @RequestParam(name = "q", required = false) String q,
            @RequestParam(name = "tipo", required = false) TipoObjeto tipo,
            @RequestParam(name = "categoriaId", required = false) Long categoriaId) {
        return ResponseEntity.ok(objetoService.buscar(q, tipo, categoriaId));
    }

    @GetMapping("/categoria/{id}")
    public ResponseEntity<List<ObjetoResponse>> porCategoria(@PathVariable Long id) {
        return ResponseEntity.ok(objetoService.porCategoria(id));
    }

    @GetMapping("/ubicacion")
    public ResponseEntity<List<ObjetoResponse>> porUbicacion(@RequestParam(name = "ubicacion", required = false) String ubicacion) {
        return ResponseEntity.ok(objetoService.porUbicacion(ubicacion));
    }

    @GetMapping("/fecha")
    public ResponseEntity<List<ObjetoResponse>> porFecha(
            @RequestParam(name = "desde", required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate desde,
            @RequestParam(name = "hasta", required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate hasta) {
        return ResponseEntity.ok(objetoService.porFecha(desde, hasta));
    }

    @GetMapping("/{id}/coincidencias")
    public ResponseEntity<List<CoincidenciaSugeridaResponse>> coincidencias(@PathVariable Long id) {
        return ResponseEntity.ok(objetoService.coincidencias(id));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ObjetoResponse> obtener(@PathVariable Long id) {
        return ResponseEntity.ok(objetoService.obtener(id));
    }

    @GetMapping
    public ResponseEntity<List<ObjetoResponse>> listar(
            @RequestParam(name = "tipo", required = false) TipoObjeto tipo,
            @RequestParam(name = "estado", required = false) EstadoObjeto estado,
            @RequestParam(name = "categoriaId", required = false) Long categoriaId) {
        return ResponseEntity.ok(objetoService.listar(tipo, estado, categoriaId));
    }

    @PostMapping
    public ResponseEntity<ObjetoResponse> crear(@Valid @RequestBody ObjetoRequest request) {
        UserPrincipal usuario = usuarioActual();
        return ResponseEntity.status(HttpStatus.CREATED).body(objetoService.crear(request, usuario.getId()));
    }

    @PutMapping("/{id}")
    public ResponseEntity<ObjetoResponse> actualizar(@PathVariable Long id,
                                                     @Valid @RequestBody ObjetoRequest request) {
        UserPrincipal usuario = usuarioActual();
        return ResponseEntity.ok(objetoService.actualizar(id, request, usuario.getId(), usuario.getRol()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        UserPrincipal usuario = usuarioActual();
        objetoService.eliminar(id, usuario.getId(), usuario.getRol());
        return ResponseEntity.noContent().build();
    }

    private UserPrincipal usuarioActual() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            throw new InvalidCredentialsException("No autenticado");
        }
        return principal;
    }
}
