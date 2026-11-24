package com.proyectointegrador.controller;

import com.proyectointegrador.dto.ObjetoResponse;
import com.proyectointegrador.entity.EstadoObjeto;
import com.proyectointegrador.entity.Objeto;
import com.proyectointegrador.entity.TipoObjeto;
import com.proyectointegrador.exception.InvalidCredentialsException;
import com.proyectointegrador.exception.ResourceNotFoundException;
import com.proyectointegrador.exception.SolicitudInvalidaException;
import com.proyectointegrador.repository.ObjetoRepository;
import com.proyectointegrador.security.UserPrincipal;
import com.proyectointegrador.service.ObjetoService;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;

/**
 * POST /api/objetos/{id}/devuelto: quien encontró un objeto (y lo tiene en mano) avisa que ya lo
 * devolvió a su dueño. La publicación queda «Recuperado · caso cerrado»: no se puede reclamar y sus
 * conversaciones quedan solo para leer. Los objetos en custodia los cierra la biblioteca al entregarlos.
 */
@RestController
public class DevolucionController {

    private final ObjetoRepository objetos;
    private final ObjetoService objetoService;

    public DevolucionController(ObjetoRepository objetos, ObjetoService objetoService) {
        this.objetos = objetos;
        this.objetoService = objetoService;
    }

    @PostMapping("/api/objetos/{id}/devuelto")
    @Transactional
    public ObjetoResponse marcarDevuelto(@PathVariable Long id) {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null || !(a.getPrincipal() instanceof UserPrincipal yo)) throw new InvalidCredentialsException("No autenticado");
        Objeto objeto = objetos.findByIdForUpdate(id)
                .filter(o -> o.getUsuario().getId().equals(yo.getId()))
                .orElseThrow(() -> new ResourceNotFoundException("Objeto no encontrado con id " + id));
        if (objeto.getTipo() != TipoObjeto.ENCONTRADO) {
            throw new SolicitudInvalidaException("Solo quien encontró un objeto puede marcarlo como devuelto.");
        }
        if (objeto.getEstado() == EstadoObjeto.RECUPERADO) return objetoService.toResponse(objeto);
        if (objeto.getEstado() != EstadoObjeto.ACTIVO) {
            throw new SolicitudInvalidaException(objeto.getEstado() == EstadoObjeto.EN_CUSTODIA
                    ? "Este objeto está en la biblioteca: lo cierra la biblioteca cuando su dueño lo retira."
                    : "Esta publicación ya no está activa.");
        }
        objeto.setEstado(EstadoObjeto.RECUPERADO);
        objeto.setFechaRecuperacion(LocalDateTime.now(ZoneOffset.UTC).truncatedTo(ChronoUnit.SECONDS));
        return objetoService.toResponse(objetos.saveAndFlush(objeto));
    }
}
