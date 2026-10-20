package com.proyectointegrador.controller;

import com.proyectointegrador.dto.CoincidenciaSugeridaResponse;
import com.proyectointegrador.dto.ObjetoRequest;
import com.proyectointegrador.dto.ObjetoResponse;
import com.proyectointegrador.dto.FotoResponse;
import com.proyectointegrador.dto.FiltroBusqueda;
import com.proyectointegrador.entity.EstadoObjeto;
import com.proyectointegrador.entity.TipoObjeto;
import com.proyectointegrador.exception.InvalidCredentialsException;
import com.proyectointegrador.security.UserPrincipal;
import com.proyectointegrador.service.ObjetoService;
import com.proyectointegrador.service.FotoService;
import com.proyectointegrador.service.BusquedaService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

@RestController
@RequestMapping("/api/objetos")
public class ObjetoController {

    private final ObjetoService objetoService;
    private final FotoService fotoService;
    private final BusquedaService busquedaService;

    public ObjetoController(ObjetoService objetoService, FotoService fotoService,
                            BusquedaService busquedaService) {
        this.objetoService = objetoService;
        this.fotoService = fotoService;
        this.busquedaService = busquedaService;
    }

    @GetMapping("/buscar")
    public ResponseEntity<List<ObjetoResponse>> buscar(@ModelAttribute FiltroBusqueda filtros) {
        return ResponseEntity.ok(busquedaService.buscar(filtros));
    }

    @GetMapping("/categoria/{id}")
    public ResponseEntity<List<ObjetoResponse>> porCategoria(@PathVariable Long id) {
        return ResponseEntity.ok(objetoService.porCategoria(id));
    }

    @GetMapping("/ubicacion")
    public ResponseEntity<List<ObjetoResponse>> porUbicacion(@ModelAttribute FiltroBusqueda filtros) {
        return ResponseEntity.ok(busquedaService.buscar(filtros));
    }

    @GetMapping("/fecha")
    public ResponseEntity<List<ObjetoResponse>> porFecha(@ModelAttribute FiltroBusqueda filtros) {
        return ResponseEntity.ok(busquedaService.porFecha(filtros));
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
        return ResponseEntity.ok(objetoService.actualizar(id, request, usuario.getId()));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> eliminar(@PathVariable Long id) {
        UserPrincipal usuario = usuarioActual();
        objetoService.eliminar(id, usuario.getId());
        return ResponseEntity.noContent().build();
    }

    @PostMapping(value = "/{id}/fotos", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<FotoResponse> subirFoto(@PathVariable Long id,
                                                   @RequestPart("archivo") MultipartFile archivo) {
        FotoResponse foto = fotoService.agregar(id, usuarioActual().getId(), archivo);
        return ResponseEntity.status(HttpStatus.CREATED).body(foto);
    }

    private UserPrincipal usuarioActual() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
            throw new InvalidCredentialsException("No autenticado");
        }
        return principal;
    }
}
