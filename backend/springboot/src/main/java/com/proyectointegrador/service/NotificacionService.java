package com.proyectointegrador.service;

import com.proyectointegrador.dto.NotificacionResponse;
import com.proyectointegrador.exception.ResourceNotFoundException;
import com.proyectointegrador.repository.NotificacionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class NotificacionService {

    private final NotificacionRepository notificacionRepository;

    public NotificacionService(NotificacionRepository notificacionRepository) {
        this.notificacionRepository = notificacionRepository;
    }

    @Transactional(readOnly = true)
    public List<NotificacionResponse> listar(Long usuarioId) {
        return notificacionRepository.findByUsuarioIdOrderByFechaCreacionDesc(usuarioId).stream()
                .map(NotificacionResponse::from)
                .toList();
    }

    @Transactional
    public NotificacionResponse marcarLeida(Long id, Long usuarioId) {
        var notificacion = notificacionRepository.findByIdAndUsuarioId(id, usuarioId)
                .orElseThrow(() -> new ResourceNotFoundException("Notificación no encontrada con id " + id));
        notificacion.setLeida(Boolean.TRUE);
        notificacionRepository.save(notificacion);
        return NotificacionResponse.from(notificacion);
    }
}
