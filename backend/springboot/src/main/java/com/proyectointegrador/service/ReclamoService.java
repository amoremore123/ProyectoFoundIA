package com.proyectointegrador.service;

import com.proyectointegrador.dto.ReclamoResponse;
import com.proyectointegrador.entity.EstadoObjeto;
import com.proyectointegrador.entity.Reclamo;
import com.proyectointegrador.entity.TipoObjeto;
import com.proyectointegrador.exception.ReclamoInvalidoException;
import com.proyectointegrador.exception.ResourceNotFoundException;
import com.proyectointegrador.repository.ObjetoRepository;
import com.proyectointegrador.repository.ReclamoRepository;
import com.proyectointegrador.repository.UsuarioRepository;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReclamoService {

    private final ObjetoRepository objetoRepository;
    private final ReclamoRepository reclamoRepository;
    private final UsuarioRepository usuarioRepository;
    private final NotificacionService notificacionService;

    public ReclamoService(ObjetoRepository objetoRepository, ReclamoRepository reclamoRepository,
                          UsuarioRepository usuarioRepository, NotificacionService notificacionService) {
        this.objetoRepository = objetoRepository;
        this.reclamoRepository = reclamoRepository;
        this.usuarioRepository = usuarioRepository;
        this.notificacionService = notificacionService;
    }

    @Transactional
    public ReclamoResponse reclamar(Long objetoId, Long usuarioId) {
        var objeto = objetoRepository.findByIdForUpdate(objetoId)
                .orElseThrow(() -> new ResourceNotFoundException("Objeto no encontrado con id " + objetoId));
        if (objeto.getEstado() != EstadoObjeto.ACTIVO || objeto.getTipo() != TipoObjeto.ENCONTRADO) {
            throw new ReclamoInvalidoException("Solo se puede reclamar un objeto encontrado que siga activo");
        }
        if (objeto.getUsuario().getId().equals(usuarioId)) {
            throw new AccessDeniedException("No puedes reclamar tu propia publicación");
        }

        var existente = reclamoRepository.findByObjetoIdAndUsuarioId(objetoId, usuarioId);
        if (existente.isPresent()) {
            return ReclamoResponse.from(existente.get(), true);
        }

        var usuario = usuarioRepository.findById(usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Usuario no encontrado con id " + usuarioId));
        Reclamo reclamo = new Reclamo();
        reclamo.setObjeto(objeto);
        reclamo.setUsuario(usuario);
        Reclamo guardado = reclamoRepository.saveAndFlush(reclamo);
        notificacionService.notificarReclamo(objeto.getUsuario(), objetoId);
        return ReclamoResponse.from(guardado, false);
    }
}
