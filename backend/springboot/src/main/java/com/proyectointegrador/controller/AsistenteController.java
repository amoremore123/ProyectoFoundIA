package com.proyectointegrador.controller;

import com.proyectointegrador.dto.AsistenteRequest;
import com.proyectointegrador.dto.AsistenteResponse;
import com.proyectointegrador.dto.FotoBusquedaRequest;
import com.proyectointegrador.dto.FotoBusquedaResponse;
import com.proyectointegrador.dto.SugerenciaPublicacionRequest;
import com.proyectointegrador.dto.SugerenciaPublicacionResponse;
import com.proyectointegrador.exception.InvalidCredentialsException;
import com.proyectointegrador.security.AsistenteRateLimiter;
import com.proyectointegrador.security.UserPrincipal;
import com.proyectointegrador.service.AsistenteService;
import com.proyectointegrador.service.BusquedaPorFotoService;
import com.proyectointegrador.service.SugerenciaPublicacionService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * H45: POST /api/asistente/interpretar
 *
 * Es público para que el asistente funcione sin iniciar sesión. Si llega un
 * token válido, el JwtAuthenticationFilter deja la sesión en el contexto y la
 * respuesta añade el resumen propio (solo cifras).
 */
@RestController
@RequestMapping("/api/asistente")
public class AsistenteController {

    private final AsistenteService asistenteService;
    private final AsistenteRateLimiter rateLimiter;
    private final SugerenciaPublicacionService sugerencias;
    private final BusquedaPorFotoService busquedaPorFoto;

    public AsistenteController(AsistenteService asistenteService, AsistenteRateLimiter rateLimiter,
                               SugerenciaPublicacionService sugerencias) {
        this(asistenteService, rateLimiter, sugerencias, null);
    }

    @org.springframework.beans.factory.annotation.Autowired
    public AsistenteController(AsistenteService asistenteService, AsistenteRateLimiter rateLimiter,
                               SugerenciaPublicacionService sugerencias, BusquedaPorFotoService busquedaPorFoto) {
        this.asistenteService = asistenteService;
        this.rateLimiter = rateLimiter;
        this.sugerencias = sugerencias;
        this.busquedaPorFoto = busquedaPorFoto;
    }

    /**
     * POST /api/asistente/buscar-por-foto. Público como el asistente: la IA describe el objeto
     * de la foto y la web busca entre las publicaciones reales. La foto no se guarda.
     */
    @PostMapping("/buscar-por-foto")
    public ResponseEntity<FotoBusquedaResponse> buscarPorFoto(
            @Valid @RequestBody FotoBusquedaRequest solicitud,
            HttpServletRequest request) {
        Long usuarioId = usuarioActualId();
        String clave = usuarioId == null ? "ip:" + request.getRemoteAddr() : "usuario:" + usuarioId;
        rateLimiter.verificar(clave);
        return ResponseEntity.ok(busquedaPorFoto.describir(solicitud.imagen(), clave));
    }

    /**
     * H49: POST /api/asistente/sugerir-publicacion. Requiere sesión (Publicar también la pide).
     * Devuelve una sugerencia y no guarda nada.
     */
    @PostMapping("/sugerir-publicacion")
    public ResponseEntity<SugerenciaPublicacionResponse> sugerirPublicacion(
            @Valid @RequestBody SugerenciaPublicacionRequest solicitud) {
        Long usuarioId = usuarioActualId();
        if (usuarioId == null) throw new InvalidCredentialsException("No autenticado");
        String clave = "usuario:" + usuarioId;
        rateLimiter.verificar(clave);
        return ResponseEntity.ok(sugerencias.sugerir(solicitud, clave));
    }

    @PostMapping("/interpretar")
    public ResponseEntity<AsistenteResponse> interpretar(
            @Valid @RequestBody AsistenteRequest solicitud,
            HttpServletRequest request) {
        Long usuarioId = usuarioActualId();
        String clave = usuarioId == null
                ? "ip:" + request.getRemoteAddr()
                : "usuario:" + usuarioId;
        rateLimiter.verificar(clave);
        return ResponseEntity.ok(asistenteService.interpretar(solicitud.mensaje(), usuarioId));
    }

    private Long usuarioActualId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication != null && authentication.getPrincipal() instanceof UserPrincipal principal) {
            return principal.getId();
        }
        return null;
    }
}
